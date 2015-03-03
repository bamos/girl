package io.github.bamos

import com.typesafe.scalalogging.Logger
import org.jsoup.{Jsoup,HttpStatusException,UnsupportedMimeTypeException}
import org.kohsuke.github.{GitHub,GHRepository}
import org.slf4j.LoggerFactory
import spray.caching.{LruCache,Cache}

import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.util.Try

import java.io.IOException
import java.net.{MalformedURLException,SocketTimeoutException}
import javax.net.ssl.SSLProtocolException


case class ReadmeAnalysis(totalLinks: Int, checkedLinks: Int,
  brokenLinks: Seq[(String,String)])

object Girl {
  import scala.concurrent.ExecutionContext.Implicits.global

  val logger = Logger(LoggerFactory.getLogger(this.getClass.getName))
  val gh = GitHub.connectUsingOAuth(sys.env("GITHUB_TOKEN"))
  val reqFollowers = 50
  val maxLinksPerRepo = 100
  val initialTimeoutMs = 2000
  val maxURLAttempts = 2
  val ua = "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:5.0) Gecko/20100101 Firefox/5.0"

  def getRepoBrokenLinks(userName: String, repoName: String) = {
    logger.info(s"getRepoBrokenLinks: $userName")
    val user = gh.getUser(userName)
    if (Whitelist.users.contains(userName.toLowerCase) ||
        user.getFollowersCount() > reqFollowers) {
      val repo = user.getRepository(repoName)
      val analysis = analyzeRepo(repo)
      html.index(userName,Seq((repoName,analysis)),
        analysis.totalLinks, analysis.checkedLinks,
        analysis.brokenLinks.size).toString
    } else {
      html.whitelist(userName, reqFollowers).toString
    }
  }

  def getUserBrokenLinks(userName: String) = {
    logger.info(s"getUserBrokenLinks: $userName")
    val user = gh.getUser(userName)
    if (Whitelist.users.contains(userName.toLowerCase) ||
        user.getFollowersCount() > reqFollowers) {
      val repos = user.getRepositories().par
      val allBrokenLinks = repos.collect{
        case (repoName,repo) if !repo.isPrivate && !repo.isFork =>
          val readmeAnalysis = analyzeRepo(repo)
          (repoName,readmeAnalysis)}
        .toSeq.seq.sortBy(_._1)
      val numTotal = allBrokenLinks.map(_._2.totalLinks).reduce(_+_)
      val numChecked = allBrokenLinks.map(_._2.checkedLinks).reduce(_+_)
      val numBroken = allBrokenLinks.map(_._2.brokenLinks.size).reduce(_+_)
      html.index(userName,
        allBrokenLinks,numTotal,numChecked,numBroken).toString
    } else {
      html.whitelist(userName, reqFollowers).toString
    }
  }

  private def analyzeRepo(repo: GHRepository): ReadmeAnalysis = {
    Try(analyzeReadme(repo.getReadme().getHtmlUrl()))
      .getOrElse(ReadmeAnalysis(0,0,Seq.empty[(String,String)]))
  }

  private def analyzeReadme(url: String): ReadmeAnalysis = {
    val readme_doc = Jsoup.connect(url).get()
    val anchors = readme_doc.select("div#readme").select("a[href]")
    val trimmedAnchors = anchors.take(maxLinksPerRepo).par
    val invalidLinks = trimmedAnchors
      .map(_.attr("abs:href"))
      .flatMap(checkURL(_))
      .seq
    ReadmeAnalysis(anchors.size, trimmedAnchors.size, invalidLinks)
  }

  // Output is None if the URL is valid and a tuple with the
  // url checked and error message string otherwise.
  private def checkURL(url:String, attemptNum:Int=1):
      Option[(String,String)] = {
    if (url.startsWith("mailto:")) return None
    try {
      val doc = Jsoup.connect(url)
        .userAgent(ua)
        .timeout(initialTimeoutMs*attemptNum)
        .execute()
      logger.info(Seq(url,doc.statusCode).mkString(","))
      if (doc.statusCode != 200) Some(url,"Status is not 200")
      else None
    } catch {
      case _: UnsupportedMimeTypeException | _: SSLProtocolException =>
        None
      case e: SocketTimeoutException =>
        // A timeout might be a slow page that doesn't
        // respond within soon enough. Retry once.
        logger.info(Seq(url,e,attemptNum).map(_.toString).mkString(","))
        if (attemptNum < maxURLAttempts) checkURL(url,attemptNum+1)
        else Some(url,"Timed out")
      case e: Throwable => {
        logger.info(Seq(url,e).map(_.toString).mkString(", "))
        Some(url,"Other exception")
      }
    }
  }
}
