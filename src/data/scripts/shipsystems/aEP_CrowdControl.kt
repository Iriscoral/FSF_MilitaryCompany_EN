package data.scripts.shipsystems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.DamageType
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript.StatusData
import com.fs.starfarer.api.util.IntervalUtil
import combat.impl.aEP_BaseCombatEffect
import combat.plugin.aEP_CombatEffectPlugin
import combat.util.aEP_DataTool
import combat.util.aEP_Tool
import data.scripts.shipsystems.aEP_system.FortressShieldStats
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lazywizard.lazylib.combat.AIUtils
import java.awt.Color

class aEP_CrowdControl : BaseShipSystemScript() {
  companion object{
    //REDUCE_MULT 是 1-x
    const val DAMAGE_REDUCE_MULT = 0.60f
    const val BONUS_ARC = -0f

    //REDUCE_MULT 是 1-x
    const val SPEED_REDUCE_MULT = 0.75f
    const val RANGE = 600f
    const val ARC = 60f
    const val ID = "aEP_CrowdControl"
    var EMP_COLOR = Color(195,125,255,255)
    var EMP_COLOR2 = Color(255,175,255,255)


    const val SELF_MAX_SPEED_REDUCE_MULT = 0.35f
  }



  private lateinit var ship: ShipAPI
  private val arcTimer = IntervalUtil(0.5f,0.5f)

  override fun apply(stats: MutableShipStatsAPI, id: String, state: ShipSystemStatsScript.State, effectLevel: Float) {
    //不知道为什么，必须把这2个修改放前面，ai才会用，所有的impl效果都要写在后面
    //推断可能是在ai类中使用一个虚拟的shipAPI调用apply方法来计算护盾实际承受伤害，如果先进行null检测，可能得不到结果所以ai不用
    stats.shieldUnfoldRateMult.modifyFlat(ID, 1f)
    stats.shieldDamageTakenMult.modifyMult(ID, 1f - DAMAGE_REDUCE_MULT * effectLevel)
    stats.shieldUpkeepMult.modifyMult(ID, 0f)


    //复制粘贴
    ship = (stats.entity?:return) as ShipAPI
    if (ship.shield != null){
      //战斗中动态修改arc是不会生效的
      val baseRad = ship.mutableStats.shieldArcBonus.computeEffective(ship.hullSpec.shieldSpec.arc)
      ship.shield.arc = MathUtils.clamp(baseRad + BONUS_ARC * effectLevel, 0f, 360f)

      ship.shield.ringColor
    }


    val amount = aEP_Tool.getAmount(ship)
    arcTimer.advance(amount)
    if(arcTimer.intervalElapsed()){

      var nearest : ShipAPI? = null
      var dist = Float.MAX_VALUE
      for(e in AIUtils.getNearbyEnemies(ship, RANGE)){
        if(e.isFighter || e.isDrone || e.isStationModule) continue
        val shortestRotate = MathUtils.getShortestRotation(ship.facing, VectorUtils.getAngle(ship.location, e.location))
        if(shortestRotate > ARC/2f || shortestRotate < -ARC/2f) continue
        val d = MathUtils.getDistance(ship, e)
        if(d < dist){
          nearest = e
          dist = d
        }
      }

      nearest?.run {
        Global.getCombatEngine().spawnEmpArcPierceShields(
          ship,
          ship.location,
          ship,
          nearest,
          DamageType.ENERGY,10f,10f,
          RANGE + nearest.collisionRadius,
          "tachyon_lance_emp_impact",
           12f,
          EMP_COLOR, EMP_COLOR2)

        //如果已经有减速buff，刷新时间
        if(nearest.customData.containsKey(ID) ){
          (nearest.customData[ID] as SlowEffect).time = 0f
        //如果没有，上buff，放进customData，注意必须使用setCustomData()
        }else{
          val slowBuff = SlowEffect(2f, nearest, SPEED_REDUCE_MULT)
          nearest.setCustomData(ID, slowBuff)
          aEP_CombatEffectPlugin.addEffect(slowBuff)
        }


      }

    }
  }

  override fun unapply(stats: MutableShipStatsAPI, id: String) {
    //复制粘贴
    ship = (stats.entity?:return) as ShipAPI


    if (ship.shield != null){
      val baseRad = ship.mutableStats.shieldArcBonus.computeEffective(ship.hullSpec.shieldSpec.arc)
      ship.shield.arc = baseRad
    }


    stats.shieldUnfoldRateMult.unmodify(ID)
    stats.shieldDamageTakenMult.unmodify(ID)
    stats.shieldUpkeepMult.unmodify(ID)

  }

  override fun getStatusData(index: Int, state: ShipSystemStatsScript.State?, effectLevel: Float): StatusData? {
    if (index == 0) {
      return StatusData(String.format(aEP_DataTool.txt("aEP_FortressShieldStats01"), (DAMAGE_REDUCE_MULT * 100f * effectLevel).toInt().toString() + "%"), false)
    }
    return null
  }


  internal class SlowEffect(lifetime : Float, val target:ShipAPI, val maxSlowReduceMult:Float)
    : aEP_BaseCombatEffect(lifetime, target) {

    val checkTimer = IntervalUtil(0.25f,0.25f)

    init {
      //target.mutableStats.maxTurnRate.modifyMult(ID,maxSlowMult)
      //target.mutableStats.turnAcceleration.modifyMult(ID,maxSlowMult)

      target.mutableStats.maxSpeed.modifyMult(ID,1f - maxSlowReduceMult)
      //target.mutableStats.acceleration.modifyMult(ID,maxSlowMult)
      //target.mutableStats.deceleration.modifyMult(ID,maxSlowMult)

    }

    override fun advanceImpl(amount: Float) {
      checkTimer.advance(amount)
      if(checkTimer.intervalElapsed()){
        //target.mutableStats.maxTurnRate.modifyMult(ID,maxSlowMult)
        //target.mutableStats.turnAcceleration.modifyMult(ID,maxSlowMult)

        target.mutableStats.maxSpeed.modifyMult(ID,1f - maxSlowReduceMult * (lifeTime-time)/lifeTime)
        //target.mutableStats.acceleration.modifyMult(ID,maxSlowMult)
        //target.mutableStats.deceleration.modifyMult(ID,maxSlowMult)
      }
    }

    override fun readyToEnd() {
      if(target.customData.containsKey(ID)){
        target.customData.remove(ID)
      }

      target.mutableStats.maxTurnRate.unmodify(ID)
      target.mutableStats.turnAcceleration.unmodify(ID)

      target.mutableStats.maxSpeed.unmodify(ID)
      target.mutableStats.acceleration.unmodify(ID)
      target.mutableStats.deceleration.unmodify(ID)
    }
  }
}