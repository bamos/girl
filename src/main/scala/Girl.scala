package io.github.bamos

import com.typesafe.scalalogging.Logger
import org.jsoup.{Jsoup,HttpStatusException,UnsupportedMimeTypeException}
import org.kohsuke.github.{GitHub,GHRepository}
import org.slf4j.LoggerFactory
import spray.caching.{LruCache,Cache}

import scala.collection.JavaConversions._
import scala.concurrent.duration._

import java.io.IOException
import java.net.{MalformedURLException,SocketTimeoutException}
import javax.net.ssl.SSLProtocolException


object Girl {
  import scala.concurrent.ExecutionContext.Implicits.global

  val logger = Logger(LoggerFactory.getLogger(this.getClass.getName))
  val gh = GitHub.connectUsingOAuth(sys.env("GITHUB_TOKEN"))
  val reqFollowers = 50

  val repoCache: Cache[String] = LruCache(timeToLive = 24 hours)
  def getRepoBrokenLinksMemoized(userName: String, repoName: String) =
    repoCache(userName+"/"+repoName) {
      getRepoBrokenLinks(userName,repoName)
    }

  def getRepoBrokenLinks(userName: String, repoName: String) = {
    logger.info(s"getRepoBrokenLinks: $userName/$repoName")
    val user = gh.getUser(userName)
    if (Whitelist.users.contains(userName.toLowerCase) ||
        user.getFollowersCount() > reqFollowers) {
      val repo = user.getRepository(repoName)
      html.index(userName,Seq((repoName,getBrokenLinks(repo)))).toString
    } else {
      html.whitelist(userName, reqFollowers).toString
    }
  }

  val userNameCache: Cache[String] = LruCache(timeToLive = 24 hours)
  def getUserBrokenLinksMemoized(userName: String) =
    userNameCache(userName) {getUserBrokenLinks(userName)}

  def getUserBrokenLinks(userName: String) = {
    logger.info(s"getUserBrokenLinks: $userName")
    val user = gh.getUser(userName)
    if (Whitelist.users.contains(userName.toLowerCase) ||
        user.getFollowersCount() > reqFollowers) {
      val repos = user.getRepositories().par
      val allBrokenLinks = repos.collect{
        case (repoName,repo) if !repo.isPrivate =>
          (repoName,getBrokenLinks(repo))}
      html.index(userName,allBrokenLinks.toSeq.seq.sortBy(_._1)).toString
    } else {
      html.whitelist(userName, reqFollowers).toString
    }
  }

  private def getBrokenLinks(repo: GHRepository): Seq[String] = {
    try analyzeReadme(repo.getReadme().getHtmlUrl())
    catch { case _: Throwable => Seq() }
  }

  private def analyzeReadme(url: String): Seq[String] = {
    val readme_doc = Jsoup.connect(url).get()
    val links = readme_doc.select("div#readme").select("a[href]")
    val invalidLinks = links.map(_.attr("abs:href")).par.filter(!isValidURL(_))
    invalidLinks.seq
  }

  private def isValidURL(url: String, attempt_num: Int = 1): Boolean = {
    if (url.startsWith("mailto:")) return true
    try {
      val doc = Jsoup.connect(url)
        .userAgent("Mozilla/5.0 (Windows NT 6.1; WOW64; rv:5.0) Gecko/20100101 Firefox/5.0")
        .timeout(2000*attempt_num)
        .execute()
      logger.info(url,doc.statusCode)
      if (doc.statusCode != 200) false
      else true
    } catch {
      case _: UnsupportedMimeTypeException | _: SSLProtocolException => true
      case e: SocketTimeoutException =>
        // A timeout might be a slow page that doesn't
        // respond within soon enough. Retry once.
        logger.info(url,e,attempt_num)
        if (attempt_num < 2) isValidURL(url,attempt_num+1)
        else false
      case e: Throwable => {
        logger.info(url,e,attempt_num)
        false
      }
    }
  }
}
