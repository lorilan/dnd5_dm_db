package dnd5_dm_db.xml_parse

import dnd5_dm_db.FromXml
import dnd5_dm_db.model._
import dnd5_dm_db.xml_parse.Utils._

import scala.xml.Node
import LocalXmlParser._
object SpellXmlParser extends FromXml[Spell]
  with DnDMeasuresXmlParser {

  def componentsFromXml(node : Node): List[Components] =
    List[Option[Components]](
      singleAttribute(node, "verbose") match {
        case "true" => Some(Verbose)
        case _ => None
      },
      singleAttribute(node, "somatic") match {
        case "true" => Some(Somatic)
        case _ => None
      },
      singleAttribute(node, "material") match {
        case "true" => Some(Material(localFromXml(node)))
        case _ => None
      }).flatten

  def fromXml(spell: Node): Spell = {
    Spell( localFromXml(spell \ "name")  ,
      (spell \ "level").text.toInt ,
      MagicSchool.fromString(spell \ "type"),
      timeFromXml(spell \ "ctime"),
      lengthFromXml(spell \ "range"),
      (spell \ "areaOfEffect").toNodeOption map areaOfEffectFromXml,
      componentsFromXml(spell \ "components"),
      spell \ "duration" map (n => (timeFromXml(n), optionBooleanAttribute(n, "concentration"))),
      localFromXml(spell \ "description" )  ,
      (spell \ "higher-level-description" ).toNodeOption map localFromXml,
      (spell \ "source").toNodeOption map Source.fromXml
    )
  }

}