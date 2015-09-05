package io.github.bamos

import com.typesafe.scalalogging.Logger
import org.jsoup.{Jsoup,HttpStatusException,UnsupportedMimeTypeException}
import org.slf4j.LoggerFactory
import spray.caching.{LruCache,Cache}

import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.util.Try

import java.io.IOException
import java.net.{MalformedURLException,SocketTimeoutException}
import javax.net.ssl.SSLProtocolException

import org.kohsuke.github.{GitHub,GHRepository}
import org.eclipse.egit.github.core.{Repository,SearchRepository}
import org.eclipse.egit.github.core.client.GitHubClient
import org.eclipse.egit.github.core.service.{ContentsService,RepositoryService,UserService}

case class ReadmeAnalysis(totalLinks: Int, checkedLinks: Int,
  brokenLinks: Seq[(String,String)])

// Eclipse GitHub (for searching)
object egh {
  val client = (new GitHubClient()).setOAuth2Token(sys.env("GITHUB_TOKEN"))
  val user = new UserService(client)
  val repo = new RepositoryService(client)
  val contents = new ContentsService(client)
}

object Girl {
  import scala.concurrent.ExecutionContext.Implicits.global

  val logger = Logger(LoggerFactory.getLogger(this.getClass.getName))
  val gh = GitHub.connectUsingOAuth(sys.env("GITHUB_TOKEN")) // kohsuke
  val reqFollowers = 50
  val maxLinksPerRepo = 100
  val initialTimeoutMs = 4000
  val maxURLAttempts = 2
  val numTop = 1000
  val ua = "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:5.0) Gecko/20100101 Firefox/5.0"

  val repoCache: Cache[String] = LruCache(timeToLive=24 hours)
  def getRepoBrokenLinksMemoized(userName: String, repoName: String) =
    repoCache(userName+"/"+repoName) {
      getRepoBrokenLinks(userName,repoName)
    }

  val userCache: Cache[String] = LruCache(timeToLive=24 hours)
  def getUserBrokenLinksMemoized(userName: String) =
    repoCache(userName) { getUserBrokenLinks(userName) }

  val topCache: Cache[String] = LruCache(timeToLive=24 hours)
  def getTopMemoized() = topCache() {getTop()}

  def getRepoBrokenLinks(userName: String, repoName: String) = {
    logger.info(s"getRepoBrokenLinks: $userName/$repoName")
    val userTry = Try(gh.getUser(userName))
    if (userTry.isSuccess) {
      val user = userTry.get
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
    } else {
      html.usernotfound(userName).toString
    }
  }

  def getUserBrokenLinks(userName: String) = {
    logger.info(s"getUserBrokenLinks: $userName")
    val userTry = Try(gh.getUser(userName))
    if (userTry.isSuccess) {
      val user = userTry.get
      if (Whitelist.users.contains(userName.toLowerCase) ||
        user.getFollowersCount() > reqFollowers) {
        val repos = user.getRepositories().par
        val allBrokenLinks = repos.collect{
          case (repoName,repo) if !repo.isPrivate && !repo.isFork =>
            val readmeAnalysis = analyzeRepo(repo)
            (repoName,readmeAnalysis)}
          .toSeq.seq.sortBy(_._1)
        val numTotal = allBrokenLinks.map(_._2.totalLinks).sum
        val numChecked = allBrokenLinks.map(_._2.checkedLinks).sum
        val numBroken = allBrokenLinks.map(_._2.brokenLinks.size).sum
        html.index(userName,
          allBrokenLinks,numTotal,numChecked,numBroken).toString
      } else {
        html.whitelist(userName, reqFollowers).toString
      }
    } else {
      html.usernotfound(userName).toString
    }
  }

  def getTop() = {
    val repos = scala.collection.mutable.Buffer[SearchRepository]()
    var pageNum = 1
    // TODO: Re-write this using better scala features.
    while (repos.size < numTop) {
      repos ++= egh.repo.searchRepositories("stars:>1",pageNum).seq
      pageNum += 1
    }
    val allBrokenLinks = repos.take(numTop).par.map{ repo =>
      val fullRepoName = repo.getOwner()+"/"+repo.getName()
      val repoObj = gh.getRepository(fullRepoName)
      (fullRepoName, repoObj.getWatchers(), analyzeRepo(repoObj))
    }.toSeq.seq.sortBy(_._2)(Ordering[Int].reverse)
    val numTotal = allBrokenLinks.map(_._3.totalLinks).sum
    val numChecked = allBrokenLinks.map(_._3.checkedLinks).sum
    val numBroken = allBrokenLinks.map(_._3.brokenLinks.size).sum
    html.top(numTop,allBrokenLinks,numTotal,numChecked,numBroken).toString
  }

  private def analyzeRepo(repo: GHRepository): ReadmeAnalysis = {
    Try(analyzeReadme(repo.getReadme().getHtmlUrl()))
      .getOrElse(ReadmeAnalysis(0,0,Seq.empty[(String,String)]))
  }

  private def analyzeReadme(readmeUrl: String): ReadmeAnalysis = {
    val readme_doc = Jsoup.connect(readmeUrl).get()
    val anchors = readme_doc.select("div#readme").select("a[href]")
    val trimmedLinks = anchors
      .map(_.attr("abs:href"))
      .filter(!_.contains(readmeUrl))
      .take(maxLinksPerRepo).par
    val invalidLinks = trimmedLinks
      .flatMap(checkURL(_))
      .seq
    ReadmeAnalysis(anchors.size, trimmedLinks.size, invalidLinks)
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
        if (Seq("127.0.0.1","localhost","0.0.0.0").exists(url.contains)) {
          None
        } else {
          Some(url,"Other exception: " + e toString)
        }
      }
    }
  }
}
