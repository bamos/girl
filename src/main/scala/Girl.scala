package io.github.bamos

import org.jsoup.Jsoup
import org.kohsuke.github.{GitHub,GHRepository}

import scala.collection.JavaConversions._
import scala.concurrent.duration._

import java.io.IOException
import java.net.{MalformedURLException,SocketTimeoutException}
import javax.net.ssl.SSLProtocolException
import org.jsoup.{HttpStatusException,UnsupportedMimeTypeException}

import spray.caching.{LruCache,Cache}

object Girl {
  import scala.concurrent.ExecutionContext.Implicits.global

  val gh = GitHub.connectUsingOAuth(
    scala.util.Properties.envOrElse("GITHUB_TOKEN",""))

  val repoCache: Cache[String] = LruCache(timeToLive = 24 hours)
  def getRepoBrokenLinksMemoized(userName: String, repoName: String) =
    repoCache(userName+"/"+repoName) {
      getRepoBrokenLinks(userName,repoName)
    }

  def getRepoBrokenLinks(userName: String, repoName: String): String = {
    val user = gh.getUser(userName)
    val repo = user.getRepository(repoName)
    getBrokenLinksStr(userName,repoName,repo)
  }

  val userNameCache: Cache[String] = LruCache(timeToLive = 24 hours)
  def getUserBrokenLinksMemoized(userName: String) = userNameCache(userName) {
    getUserBrokenLinks(userName)
  }

  def getUserBrokenLinks(userName: String): String = {
    val user = gh.getUser(userName)
    val repos = user.getRepositories().par
    val allBrokenLinks = repos.collect{
      case (repoName,repo) if !repo.isPrivate =>
        getBrokenLinksStr(userName,repoName,repo)}
    allBrokenLinks.mkString("\n\n")
  }

  private def getBrokenLinksStr(userName: String, repoName: String,
    repo: GHRepository) = {
    val brokenLinks = getBrokenLinks(repo).map(" + "+_).mkString("\n")
    s"""|# $repoName (https://github.com/$userName/$repoName)
        |
        |$brokenLinks""".stripMargin
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

  private def isValidURL(url: String): Boolean = {
    if (url.startsWith("mailto:")) return true
    try {
      val doc = Jsoup.connect(url)
        .userAgent("Mozilla/5.0 (Windows NT 6.1; WOW64; rv:5.0) Gecko/20100101 Firefox/5.0")
        .execute()
      if (doc.statusCode != 200) false
      else true
    } catch {
      case _: UnsupportedMimeTypeException | _: SSLProtocolException => true
      case e: Throwable => {
        println(url,e)
        false
      }
    }
  }
}
