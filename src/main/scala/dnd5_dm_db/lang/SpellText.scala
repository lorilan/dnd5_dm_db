package dnd5_dm_db.lang

import dnd5_dm_db.model.{Components, MagicSchool, AreaOfEffect}


trait SpellText {

  val castingTime : String

  val components : String

  val component : Components => String

  val duration : String

  val concentration : String

  val higherLevels : String

  val sAreaOfEffect : Option[AreaOfEffect] => String

  val magicSchool : MagicSchool => String

  val ritual : String

}
