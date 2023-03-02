package data.shipsystems.scripts.ai

import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipSystemAIScript
import com.fs.starfarer.api.combat.WeaponAPI
import combat.util.aEP_Tool
import org.lwjgl.util.vector.Vector2f

class aEP_RequanReloadAI: aEP_BaseSystemAI() {

  override fun advanceImpl(amount: Float, missileDangerDir: Vector2f?, collisionDangerDir: Vector2f?, target: ShipAPI?) {
    var ammoLevelTotal = 0f
    var totalOp = 0f
    for(w in ship.allWeapons){
      if(!w.slot.isBuiltIn) continue
      val op = w.spec.getOrdnancePointCost(null) + 0.01f
      ammoLevelTotal += (op * w.ammo / w.maxAmmo)
      totalOp += op
    }
    ammoLevelTotal /= totalOp
    shouldActive = false
    if(ammoLevelTotal < 0.35f && ship.fluxLevel < 0.5f){
      shouldActive = true
    }
  }
}