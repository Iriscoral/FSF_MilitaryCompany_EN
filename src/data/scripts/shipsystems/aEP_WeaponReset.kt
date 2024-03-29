package data.scripts.shipsystems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipSystemAPI
import com.fs.starfarer.api.combat.WeaponAPI
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript.StatusData
import com.fs.starfarer.util.IntervalTracker
import combat.impl.VEs.aEP_MovingSmoke
import combat.plugin.aEP_CombatEffectPlugin
import combat.plugin.aEP_CombatEffectPlugin.Mod.addEffect
import combat.util.aEP_DataTool.txt
import combat.util.aEP_Tool
import combat.util.aEP_Tool.Util.speed2Velocity
import combat.util.aEP_Tool.Util.getAmount
import combat.util.aEP_Tool.Util.getExtendedLocationFromPoint
import combat.util.aEP_Tool.Util.getTargetWidthAngleInDistance
import combat.util.aEP_Tool.Util.getWeaponOffsetInAbsoluteCoo
import combat.util.aEP_Tool.Util.isNormalWeaponSlotType
import org.lazywizard.lazylib.CollisionUtils
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import java.util.*
import kotlin.collections.HashMap

class aEP_WeaponReset: BaseShipSystemScript() {

  companion object{
    //about visual effect
    private val SMOKE_COLOR = Color(200, 200, 200, 80)
    private val SMOKE_EMIT_COLOR = Color(250, 250, 250, 180)
    val GLOW_COLOR = Color(255,72,44,118)
    val STOP_GLOW_COLOR = Color(255,235,215,150)
    private const val WORSEN_MULT = 0.5f

    private val MAX_FLUX_STORE_CAP_PERCENT = 1f

    private val JITTER_COLOR = Color(240, 50, 50, 85)

    private val FLUX_DECREASE_PERCENT: MutableMap<String, Float> = HashMap()
    private val FLUX_DECREASE_FLAT: MutableMap<String, Float> = HashMap()
    private val FLUX_RETURN_SPEED: MutableMap<String, Float> = HashMap()
    private val WEAPON_ROF_PERCENT_BONUS: MutableMap<String, Float> = HashMap()
    init {

      FLUX_DECREASE_PERCENT["aEP_fga_xiliu"] = 0.75f
      FLUX_DECREASE_PERCENT["aEP_des_cengliu"] = 0.66f
      FLUX_DECREASE_PERCENT["aEP_cru_zhongliu"] = 0.5f

      FLUX_DECREASE_FLAT["aEP_fga_xiliu"] = 100f
      FLUX_DECREASE_FLAT["aEP_des_cengliu"] = 150f
      FLUX_DECREASE_FLAT["aEP_cru_zhongliu"] = 200f

      FLUX_RETURN_SPEED["aEP_fga_xiliu"] = 0.8f
      FLUX_RETURN_SPEED["aEP_des_cengliu"] = 0.8f
      FLUX_RETURN_SPEED["aEP_cru_zhongliu"] = 0.8f

      WEAPON_ROF_PERCENT_BONUS["aEP_fga_xiliu"] = 120f
      WEAPON_ROF_PERCENT_BONUS["aEP_des_cengliu"] = 120f
      WEAPON_ROF_PERCENT_BONUS["aEP_cru_zhongliu"] = 120f
    }
  }

  var didActive = false
  private var ship: ShipAPI? = null
  private var storedHardFlux = 0f
  private var storedSoftFlux = 0f
  private val presmokeTracker = IntervalTracker(0.05f,0.05f)


  //runInIdle == true, unapply()只有在被外界强制关闭时才会调用
  override fun apply(stats: MutableShipStatsAPI?, id: String?, state: ShipSystemStatsScript.State?, effectLevel: Float) {
    //复制粘贴这行
    ship = (stats?.entity?: return) as ShipAPI
    val ship = ship as ShipAPI
    if (!ship.isAlive) return


    //激活中
    if(state == ShipSystemStatsScript.State.IN || state == ShipSystemStatsScript.State.ACTIVE || state == ShipSystemStatsScript.State.OUT){
      //执行一次
      if(!didActive){
        didActive = true
        //把presmokeTracker加满，保证进入DOWN时第一帧就会出presmoke
        presmokeTracker.advance(999f)
      }

      //储存的幅能超过极限容量的1倍但小于2倍时，性能下降，超过2倍时直接到底
      var maxStoreMult = 1f
      if(storedHardFlux+storedSoftFlux > ship.maxFlux * MAX_FLUX_STORE_CAP_PERCENT
        && storedHardFlux+storedSoftFlux < ship.maxFlux * MAX_FLUX_STORE_CAP_PERCENT * 2f) {
        maxStoreMult = 1f - WORSEN_MULT *MathUtils.clamp((storedHardFlux+storedSoftFlux-ship.maxFlux * MAX_FLUX_STORE_CAP_PERCENT)/ship.maxFlux * MAX_FLUX_STORE_CAP_PERCENT, 0f,1f)
      }
      else if(storedHardFlux+storedSoftFlux >= ship.maxFlux * MAX_FLUX_STORE_CAP_PERCENT * 2f){
        maxStoreMult = 1f - WORSEN_MULT
      }

      //ACTIVE和IN的时候吸收幅能
      if( state == ShipSystemStatsScript.State.IN || state == ShipSystemStatsScript.State.ACTIVE){
        val hard = (ship.fluxTracker.hardFlux)
        val soft = (ship.fluxTracker.currFlux - hard)
        val speedPercent = FLUX_DECREASE_PERCENT[ship.hullSpec.hullId]?:0.5f
        val speedFlat = FLUX_DECREASE_FLAT[ship.hullSpec.hullId]?:150f
        //吸收幅能，速度为当前幅能的百分比
        var toReturnThisFrame = (speedPercent * ship.currFlux + speedFlat)* getAmount(ship) / (ship.system.chargeActiveDur)
        toReturnThisFrame *= maxStoreMult
        //aEP_Tool.addDebugLog(ship.system.chargeActiveDur.toString())
        if(soft > 0){
          //限制吸收的软幅能量不超过剩余软幅能
          var toReduce = toReturnThisFrame.coerceAtMost(soft)
          ship.fluxTracker.increaseFlux(-toReduce,false)
          toReturnThisFrame -=toReduce
          storedSoftFlux +=toReduce
        }
        if(hard > 0){
          var toReduce = toReturnThisFrame.coerceAtMost(hard)
          ship.fluxTracker.increaseFlux(-toReduce,true)
          storedHardFlux += toReduce
        }

        //在激活时，从排幅口喷出短的烟雾
        if(presmokeTracker.intervalElapsed()) {
          for (w in ship.allWeapons) {
            if (!w.slot.isDecorative) continue
            if (!w.spec.weaponId.contains("aEP_marker")) continue
            val smokeLoc = w.location
            val smoke = aEP_MovingSmoke(smokeLoc)
            smoke.lifeTime = 0.35f
            smoke.fadeIn = 0.5f
            smoke.fadeOut = 0.5f
            smoke.size = 20f
            smoke.sizeChangeSpeed = 100f
            smoke.color = SMOKE_EMIT_COLOR
            smoke.setInitVel(aEP_Tool.speed2Velocity(w.currAngle, 300f))
            smoke.stopForceTimer.setInterval(0.05f, 0.05f)
            smoke.stopSpeed = 0.975f
            aEP_CombatEffectPlugin.addEffect(smoke)
          }
        }
        //后于检测，保证之前加满的第一帧能进去
        presmokeTracker.advance(aEP_Tool.getAmount(ship))
      }

      //DOWN的时候释放四周喷长烟雾特效
      if(state == ShipSystemStatsScript.State.OUT){
        if(presmokeTracker.intervalElapsed()) {
          for (w in ship.allWeapons) {
            if (!w.slot.isDecorative) continue
            if (!w.spec.weaponId.contains("aEP_marker")) continue
            val smokeLoc = w.location
            val smoke = aEP_MovingSmoke(smokeLoc)
            smoke.lifeTime = 0.75f
            smoke.fadeIn = 0.25f
            smoke.fadeOut = 0.25f
            smoke.size = 20f
            smoke.sizeChangeSpeed = 100f
            smoke.color = SMOKE_EMIT_COLOR
            smoke.setInitVel(aEP_Tool.speed2Velocity(w.currAngle, 250f))
            smoke.stopForceTimer.setInterval(0.05f, 0.05f)
            smoke.stopSpeed = 0.975f
            aEP_CombatEffectPlugin.addEffect(smoke)
          }
        }
        //后于检测，保证之前加满的第一帧能进去
        presmokeTracker.advance(aEP_Tool.getAmount(ship))
        //玩个梗，降低光束伤害
        ship.mutableStats.beamDamageTakenMult.modifyMult(id,0.2f)
      }

      //船体打光
      ship.isJitterShields = false
      ship.setJitter(ship, JITTER_COLOR, effectLevel, 1, 0f)
      ship.setJitterUnder(ship, JITTER_COLOR, effectLevel, 10, 4f + ship.collisionRadius*0.08f)

      //给武器打粒子
      ship.setWeaponGlow(
        effectLevel,
        GLOW_COLOR,
        EnumSet.of(WeaponAPI.WeaponType.BALLISTIC, WeaponAPI.WeaponType.ENERGY))

      val rofPercentBonus = WEAPON_ROF_PERCENT_BONUS[ship.hullSpec.hullId]?: 100f

      //ballistic weapon buff
      stats.ballisticRoFMult.modifyPercent(id, rofPercentBonus * effectLevel * maxStoreMult)
      //stats.ballisticAmmoRegenMult.modifyPercent(id, rofPercentBonus * effectLevel * maxStoreMult)

      //energy weapon buff
      stats.energyRoFMult.modifyPercent(id, rofPercentBonus * effectLevel * maxStoreMult)
      //stats.energyAmmoRegenMult.modifyPercent(id, rofPercentBonus * effectLevel * maxStoreMult)

      //non beam PD buff
      //stats.getNonBeamPDWeaponRangeBonus().modifyFlat(id,PD_RANGE_BONUS - RANGE_BONUS);

      //flux consume reduce
      //stats.ballisticWeaponFluxCostMod.modifyPercent(id, -FLUX_REDUCTION)
      //stats.energyWeaponRangeBonus.modifyPercent(id, -FLUX_REDUCTION)

      //取消护盾维持，防止出现把盾维吸入导致每秒幅散散了个空气的问题
      ship.mutableStats.shieldUpkeepMult.modifyMult(id,0f)


    } else{

    //系统为IDLE时
      //激活结束后运行一次
      if(didActive){
        unapply(stats, id)
      }

      //返还幅能
      var toReturnThisFrame = aEP_Tool.getRealDissipation(ship) * getAmount(ship) * (FLUX_RETURN_SPEED[ship.hullSpec.hullId]?:1f)
      if(ship.fluxTracker.isVenting) toReturnThisFrame = 0f;
      if(storedSoftFlux > 0){
        //限制返还软幅能量不超过剩余容量和储存的软幅能量
        var toAdd = toReturnThisFrame.coerceAtMost(storedSoftFlux)
        toAdd = toAdd.coerceAtMost(ship.maxFlux - ship.currFlux)
        ship.fluxTracker.increaseFlux(toAdd,false)
        //每秒最大返还量 - 软幅能返还量 = 剩下给硬幅能的量
        toReturnThisFrame -= toAdd
        storedSoftFlux -= toAdd
      }
      if(storedHardFlux > 0){
        //限制返还硬幅能量不超过剩余容量，储存的硬幅能量，和剩下给硬幅能的量
        var toAdd = toReturnThisFrame.coerceAtMost(storedHardFlux)
        toAdd = toAdd.coerceAtMost(toReturnThisFrame)
        toAdd = toAdd.coerceAtMost(ship.maxFlux - ship.currFlux)
        ship.fluxTracker.increaseFlux(toAdd,true)
        storedHardFlux -=toAdd
      }
    }

  }

  override fun unapply(stats: MutableShipStatsAPI, id: String?) {
    //复制粘贴这行
    ship = (stats.entity?: return) as ShipAPI
    val ship = ship as ShipAPI

    didActive = false
    //ballistic weapon buff
    stats.ballisticRoFMult.unmodify(id)
    stats.ballisticAmmoRegenMult.unmodify(id)

    //energy weapon buff
    stats.energyRoFMult.unmodify(id)
    stats.energyAmmoRegenMult.unmodify(id)

    //flux consume reduce
    stats.ballisticWeaponFluxCostMod.unmodify(id)
    stats.energyWeaponRangeBonus.unmodify(id)

    //取消玩梗的光束伤害减免
    stats.beamDamageTakenMult.unmodify(id)

    //还原护盾维持
    ship.mutableStats.shieldUpkeepMult.unmodify(id)

    spawnSmoke(ship, 30)

    ship.setWeaponGlow(
      0f,
      GLOW_COLOR,
      EnumSet.of(WeaponAPI.WeaponType.BALLISTIC, WeaponAPI.WeaponType.ENERGY))

  }

  override fun getStatusData(index: Int, state: ShipSystemStatsScript.State?, effectLevel: Float): StatusData? {
    val ship = (ship?:return null)
    if(state == ShipSystemStatsScript.State.IN || state == ShipSystemStatsScript.State.ACTIVE || state == ShipSystemStatsScript.State.OUT) {
      if (index == 0) {
        return  StatusData(txt("aEP_WeaponReset01"), false)
      }else if (index == 1){
        if(storedHardFlux+storedSoftFlux > ship.maxFlux * MAX_FLUX_STORE_CAP_PERCENT){
          var maxStoreMult = 1f
          if(storedHardFlux+storedSoftFlux > ship.maxFlux * MAX_FLUX_STORE_CAP_PERCENT
            && storedHardFlux+storedSoftFlux < ship.maxFlux * MAX_FLUX_STORE_CAP_PERCENT * 2f) {
            maxStoreMult = 1f - WORSEN_MULT *MathUtils.clamp((storedHardFlux+storedSoftFlux-ship.maxFlux * MAX_FLUX_STORE_CAP_PERCENT)/ship.maxFlux * MAX_FLUX_STORE_CAP_PERCENT, 0f,1f)
          } else if(storedHardFlux+storedSoftFlux >= ship.maxFlux * MAX_FLUX_STORE_CAP_PERCENT * 2f){
            maxStoreMult = 1f - WORSEN_MULT
          }
          return  StatusData(txt("aEP_WeaponReset02") + (maxStoreMult*100f).toInt() +" %", true)
        }
      }
    }
    return  null
  }

  override fun getInfoText(system: ShipSystemAPI?, ship: ShipAPI?): String {
    return "Stored: "+ storedSoftFlux.toInt()+" / "+ storedHardFlux.toInt()
  }

  fun spawnSmoke(ship: ShipAPI, minSmokeDist: Int) {
    var moveAngle = 0f
    val angleToTurn = getTargetWidthAngleInDistance(ship.location, getExtendedLocationFromPoint(ship.location, 0f, ship.collisionRadius), minSmokeDist.toFloat())
    while (moveAngle < 360f) {
      val outPoint = CollisionUtils.getCollisionPoint(getExtendedLocationFromPoint(ship.location, moveAngle, ship.collisionRadius + 10), ship.location, ship)
      val lifeTime = 2f
      val extendRange = 0.5f
      val speed = speed2Velocity(VectorUtils.getAngle(ship.location, outPoint), extendRange * ship.collisionRadius)
      val ms = aEP_MovingSmoke(outPoint!!)
      ms.lifeTime = lifeTime
      ms.fadeIn = 0.25f
      ms.fadeOut = 0.5f
      ms.setInitVel(speed)
      ms.size = minSmokeDist * 3f
      ms.sizeChangeSpeed = minSmokeDist * extendRange * 3f / lifeTime
      ms.color = SMOKE_COLOR
      ms.stopSpeed = 0.75f
      addEffect(ms)
      moveAngle += angleToTurn
    }
    moveAngle = 0f
    while (moveAngle < 360f) {
      val outPoint = CollisionUtils.getCollisionPoint(getExtendedLocationFromPoint(ship.location, moveAngle, ship.collisionRadius + 10), ship.location, ship)
      val lifeTime = 2f
      val extendRange = 0.5f
      val speed = speed2Velocity(VectorUtils.getAngle(ship.location, outPoint), extendRange * ship.collisionRadius + minSmokeDist * 6f)
      val ms = aEP_MovingSmoke(outPoint!!)
      ms.lifeTime = lifeTime
      ms.fadeIn = 0.25f
      ms.fadeOut = 0.5f
      ms.setInitVel(speed)
      ms.size = minSmokeDist * 6f
      ms.sizeChangeSpeed = minSmokeDist * extendRange * 6f / lifeTime
      ms.color = SMOKE_COLOR
      ms.stopSpeed = 0.75f
      addEffect(ms)
      moveAngle += angleToTurn
    }
  }
}
