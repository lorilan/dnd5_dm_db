package dnd5_dm_db

import java.io.{FileNotFoundException, File}

import akka.actor.{Actor, Props}
import dnd5_dm_db.html_gen.{Templates, SpellHtmlGen, MonsterHtmlGen}
import dnd5_dm_db.lang.langFromString
import dnd5_dm_db.model._
import dnd5_dm_db.xml_parse._
import sbt.PathFinder
import spray.http.MediaTypes._
import spray.routing._
import Constant._
import Templates._

import scala.xml.XML

object ServiceActor {
  def props( root : String) =
    Props(new ServiceActor( root))
}

// we don't implement our route structure directly in the service actor because
// we want to be able to test it independently, without having to spin up an actor
class ServiceActor(val resources : String) extends Actor with Dnd5DMDBService {

  println("Resources root directory is : " +resources)

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

  // this actor only runs our route, but you could add
  // other things here, like request stream processing
  // or timeout handling
  def receive = runRoute( theRoute /*~ {
    case RequestContext(request: HttpRequest, responder: ActorRef, unmatchedPath: Uri.Path) =>
      println(request)
      println("unmatched : " + unmatchedPath)
      ()
  }*/)

}


// this trait defines our service behavior independently from the service actor
trait Dnd5DMDBService extends HttpService {

  val resources : String
  val pathFinder : PathFinder = PathFinder(new File(resources))

  val langIndexRoute : String => Route = {
    langSegment =>
      get {
        respondWithMediaType(`text/html`) {
          complete {
            try {
              val lang = langFromString(langSegment)
              Templates.genIndex(lang)
            } catch {
              case _ : NoSuchElementException =>
                "unknown lang"
            }
          }
        }
      }
  }
  val defaultRoute : Route = path("") (langIndexRoute("eng"))

  val indexRoute = path(Segment) (langIndexRoute)

  def menuRoute : Route =
    path(Segment / "menu.html") {
      langSegment =>
        get {
          respondWithMediaType(`text/html`) {
            complete {
              try {
                implicit  val lang = langFromString(langSegment)

                val monsterFilesFinder : PathFinder = pathFinder / monsters ** "*.xml"
                val spellFilesFinder : PathFinder = pathFinder / spells ** "*.xml"
                val indexFilesFinder : PathFinder= pathFinder / indexes ** "*.xml"

                val monsterSeq = ParseSeq(monsterFilesFinder)(MonsterNameXmlParser)
                val spellSeq = ParseSeq(spellFilesFinder)(NameXmlParser)
                val indexSeq = ParseSeq(indexFilesFinder)(IndexXmlParser.withoutEntries)
                val (spellIndexSeq, monsterIndexSeq) =
                    indexSeq partition {case (_, (_,t)) => t == Spell}

                implicit val indexNameExtractor : NameExtractor[(Local, IndexType)] =
                  {case (n, _) => n}



                html_header("menu", List()) +
                  genMenu(spells, spells+"_menu", spellSeq) +
                  genMenu(monsters, monsters+"_menu", monsterSeq) +
                  genMenu(indexes, spells+"_"+indexes, spellIndexSeq) +
                  genMenu(indexes, monsters+"_"+indexes, monsterIndexSeq) +
                  html_footer

              } catch {
                case _ : NoSuchElementException =>
                  "unknown lang"
              }
            }
          }
        }
    }

  def makeXmlRoute[A](prefix : String, fromXml: FromXml[A], toHtml : ToHtml[A]) : Route =
    pathPrefix(prefix / Segment){
      langSegment =>
        path(Segment){
          idSegment =>
              get {
                respondWithMediaType(`text/html`) {
                  complete {
                    try {
                      val lang = langFromString(langSegment)
                      val id = idSegment stripSuffix ".html"
                      val node = XML.loadFile(s"$resources/$prefix/$id.xml")
                      toHtml.toHtml(id, fromXml.fromXml(node))(lang)
                    } catch {
                      case e : FileNotFoundException =>
                        e.getMessage
                      case _ : NoSuchElementException =>
                        "unknown lang"
                    }
                  }
                }
              }
        }
    }

  def fetchSpell : Retriever[Spell] = {
    id =>
      val node = XML.loadFile(s"$resources/$spells/$id.xml")
      SpellXmlParser.fromXml(node)
  }

  def fetchTrait : Retriever[Trait] = {
    id =>
      val node = XML.loadFile(s"$resources/$traits/$id.xml")
      TraitXmlParser.fromXml(node)
  }

  def fetchWeapon : Retriever[Weapon] = {
    id =>
      val node = XML.loadFile(s"$resources/$weapons/$id.xml")
      WeaponXmlParser.fromXml(node)
  }

  val getMonster : Route = makeXmlRoute(monsters, MonsterXmlParser.fromXml(fetchSpell, fetchTrait, fetchWeapon), MonsterHtmlGen)


  val getSpell : Route =
    makeXmlRoute(spells, SpellXmlParser, SpellHtmlGen)

  val indexesRoute :Route =
    pathPrefix("indexes" / Segment){
    langSegment =>
      path(Segment){
        idSegment =>
          get {
            respondWithMediaType(`text/html`) {
              complete {
                try {
                  val lang = langFromString(langSegment)
                  val id = idSegment stripSuffix ".html"
                  val node = XML.loadFile(s"$resources/indexes/$id.xml")
                  val i = IndexXmlParser.fromXml(node)
                  val htmlGen = i.typ match {
                    case Monster =>
                      IndexToHtml.monsterIndexToHtml(s"$resources/$monsters")
                    case Spell =>
                      IndexToHtml.spellIndexToHtmlOrderedByLevel(s"$resources/$spells")
                  }
                  htmlGen.toHtml(id, i)(lang)
                } catch {
                  case e : FileNotFoundException =>
                    e.getMessage
                  case _ : NoSuchElementException =>
                    "unknown lang"
                }
              }
            }
          }
      }
  }

  def getFileRouteGen(name : String) : Route =
    pathPrefix(name){
      compressResponse() {
        getFromResourceDirectory(name)
      }
    }

  val getFileRoute : Route =
    getFileRouteGen("images") ~
    getFileRouteGen("css") ~
    getFileRouteGen("js")


  val theRoute = defaultRoute ~ indexRoute ~ getMonster ~ getSpell ~ indexesRoute ~ getFileRoute ~ menuRoute

}