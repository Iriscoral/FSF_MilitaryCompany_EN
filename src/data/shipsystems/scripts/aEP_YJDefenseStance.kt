package data.shipsystems.scripts

import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import com.fs.starfarer.api.combat.ShipAPI

class aEP_YJDefenseStance : BaseShipSystemScript() {
  override fun apply(stats: MutableShipStatsAPI, id: String, state: ShipSystemStatsScript.State, effectLevel: Float) {
    val ship = (stats.entity?: return) as ShipAPI
  }

  override fun unapply(stats: MutableShipStatsAPI, id: String) {}
}