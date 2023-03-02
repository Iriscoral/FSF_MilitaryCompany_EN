package data.hullmods

import data.hullmods.aEP_BaseHullMod
import java.util.HashMap
import data.hullmods.aEP_SelfRotate
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipCommand
import combat.util.aEP_Tool

class aEP_SelfRotate : aEP_BaseHullMod() {
  companion object {
    const val id = "aEP_SelfRotate"

  }

  override fun advanceInCombat(ship: ShipAPI, amount: Float) {
    super.advanceInCombat(ship, amount)
    ship.giveCommand(ShipCommand.TURN_LEFT,null,0)

    if(ship.stationSlot?.id?.equals("MD02") == true) return
    val it = ship?.parentStation?.childModulesCopy?.iterator()
    while(it?.hasNext() == true){
      val next = it.next()
      if(next?.stationSlot?.id?.equals("MD02") == true){
        ship.facing = aEP_Tool.angleAdd(next.facing,180f)
        break
      }
    }

  }
}