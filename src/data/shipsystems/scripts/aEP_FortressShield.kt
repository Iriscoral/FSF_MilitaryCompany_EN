package data.shipsystems.scripts

import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript.StatusData

class aEP_FortressShieldStats : BaseShipSystemScript() {


  companion object{
    const val ID = "aEP_FortressShield"
    const val DAMAGE_MULT = 0.8f
  }

  override fun apply(stats: MutableShipStatsAPI?, id: String?, state: ShipSystemStatsScript.State?, effectLevel: Float) {
    //复制粘贴这行
    val ship = (stats?.entity?: return)as ShipAPI
    stats.shieldDamageTakenMult.modifyMult(ID, 1f - DAMAGE_MULT * effectLevel)
    stats.shieldUpkeepMult.modifyMult(ID, 0f)
  }

  override fun unapply(stats: MutableShipStatsAPI?, id: String?) {
    //复制粘贴这行
    val ship = (stats?.entity?: return)as ShipAPI

    stats.shieldDamageTakenMult.unmodify(ID)
    stats.shieldUpkeepMult.unmodify(ID)
  }

  override fun getStatusData(index: Int, state: ShipSystemStatsScript.State?, effectLevel: Float): StatusData? {
    return if (index == 0) {
      StatusData("shield damage taken: " + String.format("%.0f",100f - DAMAGE_MULT*100f), false)
    } else null
  }
}