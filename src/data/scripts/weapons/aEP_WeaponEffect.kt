package data.scripts.weapons

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI
import com.fs.starfarer.api.combat.listeners.DamageDealtModifier
import com.fs.starfarer.api.loading.DamagingExplosionSpec
import com.fs.starfarer.api.loading.ProjectileSpecAPI
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.WeightedRandomPicker
import combat.impl.VEs.*
import combat.impl.aEP_BaseCombatEffect
import combat.impl.aEP_Buff
import combat.impl.buff.aEP_UpKeepIncrease
import combat.impl.proj.aEP_StickOnHit
import combat.plugin.aEP_BuffEffect
import combat.plugin.aEP_BuffEffect.Companion.addThisBuff
import combat.plugin.aEP_CombatEffectPlugin
import combat.plugin.aEP_CombatEffectPlugin.Mod.addEffect
import combat.util.aEP_ID
import combat.util.aEP_ID.Companion.VECTOR2F_ZERO
import combat.util.aEP_Tool
import combat.util.aEP_Tool.Util.angleAdd
import combat.util.aEP_Tool.Util.firingSmoke
import combat.util.aEP_Tool.Util.firingSmokeNebula
import combat.util.aEP_Tool.Util.getExtendedLocationFromPoint
import combat.util.aEP_Tool.Util.spawnCompositeSmoke
import combat.util.aEP_Tool.Util.spawnSingleCompositeSmoke
import data.scripts.ai.aEP_MaoDianDroneAI
import data.scripts.campaign.intel.aEP_CruiseMissileLoadIntel
import data.scripts.campaign.intel.aEP_CruiseMissileLoadIntel.Companion.LOADING_MAP
import data.scripts.util.MagicLensFlare
import data.scripts.util.MagicRender
import data.shipsystems.scripts.aEP_MaodianDroneLaunch
import org.dark.shaders.distortion.DistortionShader
import org.dark.shaders.distortion.WaveDistortion
import org.dark.shaders.light.LightShader
import org.dark.shaders.light.StandardLight
import org.lazywizard.lazylib.CollisionUtils
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import org.lazywizard.lazylib.combat.AIUtils
import org.lazywizard.lazylib.combat.CombatUtils
import org.lazywizard.lazylib.combat.DefenseUtils
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import java.util.*

class aEP_WeaponEffect : OnFireEffectPlugin, OnHitEffectPlugin, ProximityExplosionEffect {

  override fun onHit(projectile: DamagingProjectileAPI, target: CombatEntityAPI, point: Vector2f, shieldHit: Boolean, damageResult: ApplyDamageResultAPI, engine: CombatEngineAPI) {
    //根据projId进行类查找
    projectile?: return
    var weaponId: String? = ""
    if (projectile.weapon != null) weaponId = projectile.weapon.spec.weaponId

    //查找流程结束以后返回非null，就运行对应的方法
    getEffect(projectile)?.onHit(projectile, target, point, shieldHit, damageResult, engine, weaponId)
  }

  override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI?, engine: CombatEngineAPI) {
    //根据 projId进行类查找
    projectile?: return
    val effect: Effect? = getEffect(projectile)
    var weaponId: String? = ""
    if (projectile.weapon != null) weaponId = projectile.weapon.spec.weaponId

    //查找流程结束以后返回非 null，就运行对应的方法
    effect?.onFire(projectile, weapon, engine, weaponId)
  }

  override fun onExplosion(explosion: DamagingProjectileAPI, originalProjectile: DamagingProjectileAPI) {
    //根据 projId进行类查找
    val projectile = originalProjectile?:return
    val effect: Effect? = getEffect(projectile)
    var weaponId = ""
    if (projectile.weapon != null) weaponId = projectile.weapon.spec.weaponId
    //查找流程结束以后返回非 null，就运行对应的方法
    effect?.onExplosion(explosion, projectile, weaponId)
  }

  private fun getEffect(projectile: DamagingProjectileAPI): Effect? {
    //类缓存，查找过一次的类放进 map，减少调用 forName的次数
    val cache: MutableMap<String?,Effect?> = (Global.getCombatEngine().customData["aEP_WeaponEffect"] as MutableMap<String?,Effect?>?)?: HashMap()

    //在cache里面找projId对应的类，没有就classForName查一个放进cahce
    //classForName也查不到就保持null
    val projId = projectile.projectileSpecId
    var effect: Effect? = null
    if (!cache.containsKey(projId)) {
      try {
        val e = Class.forName(aEP_WeaponEffect::class.java.getPackage().name + "." + projId)
        effect = e.newInstance() as Effect
        cache[projId] = effect
        Global.getLogger(this.javaClass).info("WeaponEffectLoaded :" +e.name)
      } catch (e: ClassNotFoundException) {
        e.printStackTrace()
      } catch (e: InstantiationException) {
        e.printStackTrace()
      } catch (e: IllegalAccessException) {
        e.printStackTrace()
      }
    } else {
      effect = cache[projId]
    }
    Global.getCombatEngine().customData["aEP_WeaponEffect"] = cache
    return effect

  }
}

/*
继承的类名要是弹丸的id
onHit不会被PROXIMITY_FUSE的弹丸触发
onHit触发在弹丸造成伤害以后
 */
open class Effect {
  constructor()
  open fun onHit(projectile: DamagingProjectileAPI?, target: CombatEntityAPI?, point: Vector2f?, shieldHit: Boolean, damageResult: ApplyDamageResultAPI?, engine: CombatEngineAPI?, weaponId: String?) {}
  open fun onFire(projectile: DamagingProjectileAPI?, weapon: WeaponAPI?, engine: CombatEngineAPI?, weaponId: String?) {}
  open fun onExplosion(explosion: DamagingProjectileAPI?, originalProjectile: DamagingProjectileAPI?, weaponId: String?) {}
}

//创伤炮
class aEP_trauma_cannon_shot : Effect() {
  override fun onFire(projectile: DamagingProjectileAPI?, weapon: WeaponAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    val ship = weapon!!.ship
    val angle = aEP_Tool.angleAdd(weapon.currAngle, MathUtils.getRandomNumberInRange(45, 135).toFloat()) //get a random angle from 45 to 135

    val angularSpeed = MathUtils.getRandomNumberInRange(-180, 180).toFloat()
    val vel = aEP_Tool.speed2Velocity(angle, MathUtils.getRandomNumberInRange(100, 300).toFloat())
    val shell = engine!!.spawnProjectile(ship,
        null,
        "aEP_trauma_cannon_eject",
        weapon.location,
        angle,
        ship.velocity) as DamagingProjectileAPI
    shell.angularVelocity = angularSpeed
    shell.velocity[vel.x] = vel.y

    addEffect(object : aEP_BaseCombatEffect() {
      val tracker  = IntervalUtil(0.1f, 0.1f)
      override fun advanceImpl(amount: Float) {
        shell.velocity.scale(0.975f)
        shell.angularVelocity = shell.angularVelocity * 0.975f
      }
    })
  }
}

//爆破锤系列
class aEP_as_missile : Effect(){
  override fun onFire(projectile: DamagingProjectileAPI?, weapon: WeaponAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    projectile ?:return
    aEP_CombatEffectPlugin.Mod.addEffect(
      object : aEP_SmokeTrail(projectile,
        20f,
        2.4f,
        20f,
        40f,
      Color(120,120,120,120)){
        override fun advanceImpl(amount: Float) {
          super.advanceImpl(amount)
          if(entity is MissileAPI ){
            entity?: return
            if((entity as MissileAPI).engineController.isAccelerating) return
            (entity as MissileAPI).flightTime = (entity as MissileAPI).flightTime - amount/2f
          }
        }
      }
    )

    //convert energy damage to kinetic, HE , frag damage
    //convert energy damage to kinetic, HE , frag damage
    val damage: DamageAPI = projectile.getDamage()
    damage.damage = damage.damage / 3f
    damage.type = DamageType.KINETIC
  }

  override fun onHit(projectile: DamagingProjectileAPI?, target: CombatEntityAPI?, point: Vector2f?, shieldHit: Boolean, damageResult: ApplyDamageResultAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    point?:return
    val projId = projectile?.projectileSpecId ?: return
    //Global.getCombatEngine().addFloatingText(projectile.getLocation(),projectile.getDamageAmount() + "", 20f ,new Color(100,100,100,100),projectile, 0.25f, 120f);
    val toUseWeaponId = "aEP_as_shot"
    val pro2 = engine!!.spawnProjectile(
      projectile.source,  //source ship
      projectile.weapon,  //source weapon,
      toUseWeaponId,  //whose proj to be use
      aEP_Tool.getExtendedLocationFromPoint(
        point,
        projectile.facing,
        -(Global.getSettings().getWeaponSpec(toUseWeaponId).projectileSpec as ProjectileSpecAPI).getMoveSpeed(
          null,
          null
        ) * 0.25f
      ),  //loc
      projectile.facing,  //facing
      null
    )
    val damage1 = (pro2 as DamagingProjectileAPI).damage
    damage1.damage = projectile.damage.damage
    damage1.type = DamageType.HIGH_EXPLOSIVE
    engine.removeEntity(projectile)

  }
}

class aEP_as_missile_large : Effect(){
  override fun onFire(projectile: DamagingProjectileAPI?, weapon: WeaponAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    projectile ?:return
    aEP_CombatEffectPlugin.Mod.addEffect(
      object : aEP_SmokeTrail (projectile,
        30f,
        3f,
        30f,
        60f,
      Color(120,120,120,150)){
        override fun advanceImpl(amount: Float) {
          super.advanceImpl(amount)
          if(entity is MissileAPI ){
            entity?: return
            if((entity as MissileAPI).engineController.isAccelerating) return
            (entity as MissileAPI).flightTime = (entity as MissileAPI).flightTime - amount/2f
          }
        }
      }
    )

    //convert energy damage to kinetic, HE , frag damage
    //convert energy damage to kinetic, HE , frag damage
    val damage: DamageAPI = projectile.getDamage()
    damage.damage = damage.damage / 3f
    damage.type = DamageType.KINETIC
  }

  override fun onHit(projectile: DamagingProjectileAPI?, target: CombatEntityAPI?, point: Vector2f?, shieldHit: Boolean, damageResult: ApplyDamageResultAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    point?: return
    val projId = projectile?.projectileSpecId ?: return
    //Global.getCombatEngine().addFloatingText(projectile.getLocation(),projectile.getDamageAmount() + "", 20f ,new Color(100,100,100,100),projectile, 0.25f, 120f);
    val toUseWeaponId = "aEP_as_shot"
    val pro2 = engine!!.spawnProjectile(
      projectile.source,  //source ship
      projectile.weapon,  //source weapon,
      toUseWeaponId,  //whose proj to be use
      aEP_Tool.getExtendedLocationFromPoint(
        point,
        projectile.facing,
        -(Global.getSettings().getWeaponSpec(toUseWeaponId).projectileSpec as ProjectileSpecAPI).getMoveSpeed(
          null,
          null
        ) * 0.25f
      ),  //loc
      projectile.facing,  //facing
      null
    )
    val damage1 = (pro2 as DamagingProjectileAPI).damage
    damage1.damage = projectile.damage.damage
    damage1.type = DamageType.HIGH_EXPLOSIVE
    engine.removeEntity(projectile)

  }
}

class aEP_as_shot : Effect(){
  override fun onFire(projectile: DamagingProjectileAPI?, weapon: WeaponAPI?, engine: CombatEngineAPI?, weaponId: String?) {

  }

  override fun onHit(projectile: DamagingProjectileAPI?, target: CombatEntityAPI?, point: Vector2f?, shieldHit: Boolean, damageResult: ApplyDamageResultAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    point?:return
    val projId = projectile?.projectileSpecId ?: return
    //Global.getCombatEngine().addFloatingText(projectile.getLocation(),projectile.getDamageAmount() + "", 20f ,new Color(100,100,100,100),projectile, 0.25f, 120f);
    if (projectile.damage.type == DamageType.HIGH_EXPLOSIVE) {
      val toUseWeaponId = "aEP_as_shot"
      val pro2 = engine!!.spawnProjectile(
        projectile.source,  //source ship
        projectile.weapon,  //source weapon,
        toUseWeaponId,  //whose proj to be use
        aEP_Tool.getExtendedLocationFromPoint(
          point,
          projectile.facing,
          -(Global.getSettings().getWeaponSpec(toUseWeaponId).projectileSpec as ProjectileSpecAPI).getMoveSpeed(
            null,
            null
          ) * 0.25f
        ),  //loc
        projectile.facing,  //facing
        null
      )
      val damage2 = (pro2 as DamagingProjectileAPI).damage
      damage2.damage = projectile.damage.damage * 2f
      damage2.type = DamageType.FRAGMENTATION
      engine.removeEntity(projectile)
    }
  }
}

//荡平反应炸弹1,2
class aEP_nuke_fighter_missile1 : Effect(){
  override fun onFire(projectile: DamagingProjectileAPI?, weapon: WeaponAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    engine?: return
    projectile?: return
    projectile.damage.modifier.modifyMult(projectile.projectileSpecId, 0.05f)

  }

  override fun onHit(projectile: DamagingProjectileAPI?, target: CombatEntityAPI?, point: Vector2f?, shieldHit: Boolean, damageResult: ApplyDamageResultAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    engine?: return
    projectile?: return
    val mine = engine.spawnProjectile(projectile.source,
      projectile.weapon,
      "aEP_nuke_fighter2",
      projectile.location,
      projectile.facing,
      projectile.velocity) as MissileAPI
    mine.hitpoints = projectile.hitpoints
  }

}

class aEP_nuke_fighter_missile2 : Effect(){
  override fun onExplosion(explosion: DamagingProjectileAPI?, originalProjectile: DamagingProjectileAPI?, weaponId: String?) {

    val SIZE_MULT = 0.75f //default = 300;

    val point = explosion?.location?: Vector2f(999999f,999999f)
    val vel = Vector2f(0f, 0f)
    val yellowSmoke = Color(250, 250, 220)
    //add red center smoke
    Global.getCombatEngine().spawnExplosion(
      point,
      vel,
      yellowSmoke,
      300f * SIZE_MULT,
      4f)

    //add white center glow
    Global.getCombatEngine().addHitParticle(
      point,
      vel,
      300 * SIZE_MULT,
      2f,
      5f,
      Color.white)

    //add yellow around glow
    Global.getCombatEngine().addSmoothParticle(
      point,
      vel,
      450 * SIZE_MULT,
      0.8f,
      4f,
      Color.yellow)

    //create distortion
    val wave = WaveDistortion(point, Vector2f(0f, 0f))
    wave.setLifetime(1f)
    wave.size = 300f * SIZE_MULT
    wave.fadeInSize(1.5f)
    wave.intensity = 160f
    wave.fadeOutIntensity(1.5f)
    DistortionShader.addDistortion(wave)

    //create smokes
    val numMax = 24
    var angle = 0f
    for (i in 0 until numMax) {
      val loc = aEP_Tool.getExtendedLocationFromPoint(point, angle, 150f * SIZE_MULT)
      val sizeGrowth = 60 * SIZE_MULT
      val sizeAtMin = 175 * SIZE_MULT
      val moveSpeed = 60f * SIZE_MULT
      val smoke = aEP_MovingSmoke(loc)
      smoke.setInitVel(aEP_Tool.speed2Velocity(VectorUtils.getAngle(point, loc), moveSpeed))
      smoke.fadeIn = 0.1f
      smoke.fadeOut = 0.3f
      smoke.lifeTime = 3f
      smoke.sizeChangeSpeed = sizeGrowth
      smoke.size = sizeAtMin
      smoke.color = Color(200, 200, 200, MathUtils.getRandomNumberInRange(50, 100))
      aEP_CombatEffectPlugin.addEffect(smoke)
      angle += 360f / numMax
    }
  }
}

//破门锥
class aEP_breakin_shot : Effect(){
  override fun onFire(projectile: DamagingProjectileAPI?, weapon: WeaponAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    super.onFire(projectile, weapon, engine, weaponId)
  }

  override fun onHit(projectile: DamagingProjectileAPI?, target: CombatEntityAPI?, point: Vector2f?, shieldHit: Boolean, damageResult: ApplyDamageResultAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    val MAX_OVERLOAD_TIME = 2f

    point?: return
    val vel = Vector2f(0f, 0f)
    if (target !is ShipAPI) return
    if (shieldHit) {
      engine!!.applyDamage(
        target,  //target
        point,  // where to apply damage
        projectile!!.damage.fluxComponent,  // amount of damage
        DamageType.KINETIC,  // damage type
        0f,  // amount of EMP damage (none)
        false,  // does this bypass shields? (no)
        true,  // does this deal soft flux? (no)
        projectile.source
      )
      if (target.fluxTracker.isOverloaded) {
        target.fluxTracker.stopOverload()
        target.fluxTracker.beginOverloadWithTotalBaseDuration(MAX_OVERLOAD_TIME)
      }
    }


    var wave = WaveDistortion(point, vel)
    wave.size = 200f
    wave.intensity = 50f
    wave.fadeInSize(0.75f)

    wave.setLifetime(0f)
    DistortionShader.addDistortion(wave)
    wave = WaveDistortion(point, vel)
    wave.size = 200f
    wave.intensity = 20f
    wave.fadeInSize(1f)
    wave.setLifetime(0f)
    DistortionShader.addDistortion(wave)


    val color = Color(240, 240, 240, 240)
    val ring = aEP_MovingSprite(point,
      Vector2f(0f, 0f),
      0f,
      VectorUtils.getAngle(point, target.getLocation()),
      0f,
      0f,
      1f,
      Vector2f(450f, 900f),
      Vector2f(8f, 24f),
      "aEP_FX.ring",
      color)
    addEffect(ring)
    CombatUtils.applyForce(target, projectile!!.velocity, 100f)
  }



}

//异象导弹
class aEP_NC_missile2 : Effect(){
  companion object{
    const val EMP_CLOUD_RADIUS = 200f
    const val EMP_CLOUD_END_RADIUS_MULT = 3f

    const val EMP_PER_HIT = 500f

    val CLOUD_COLOR = Color(99,79,233,96)
    val ARC_COLOR = Color(102,143,197,87)
    val ARC_COLOR_FRINGE = Color(255,255,255,45)
  }

  fun onFire2(projectile: DamagingProjectileAPI?, weapon: WeaponAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    /*addEffect(object : aEP_BaseCombatEffect(){
      init { init(projectile) }
      val m = projectile as MissileAPI
      var FUSE_RANGE = 125f
      var MAX_RANGE = 1600f
      var ACCELERATE_MOD = -50f
      var SLOW_PERCENT = 0.5f // speed - speed * SLOW_PERCENT (per seccond)
      var fused = false
      var EXPLOSION = Color(40, 40, 150, 100)
      override fun advanceImpl(amount: Float) {
        //get nearest enemy
        val target = AIUtils.getNearestEnemy(m) ?: return
        if(MathUtils.getDistance(target,m) > FUSE_RANGE) return
        fused = true
        cleanup()
      }

      //因为 entity已经被初始化，导弹消失，或者引信触发，都会触发这个
      override fun readyToEnd() {
        //既非引信，又非撞爆，不触发效果
        if(!fused && !m.didDamage()) return

        //engine.addFloatingText(engine.getPlayerShip().getMouseTarget(f),dist + "",20f,new Color(100,100,100,100),engine.getPlayerShip(),1f,5f);
        //create visual effect
        //create ring
        val ring = aEP_SpreadRing(1600f,
          MAX_RANGE,
          Color(40, 40, 150, 180),
          0f,
          MAX_RANGE,
          m.location)
        ring.initColor.setToColor(40f, 40f, 40f, 10f, 1f)
        ring.layers = EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_LAYER)
        addEffect(ring)

        //create distortion
        val wave = WaveDistortion(m.location, Vector2f(0f, 0f))
        wave.size = MAX_RANGE
        wave.setLifetime(1f)
        wave.fadeInSize(1f)
        wave.intensity = 200f
        wave.fadeOutIntensity(1f)
        DistortionShader.addDistortion(wave)
        //create distortion2
        val wave2 = WaveDistortion(m.location, Vector2f(0f, 0f))
        wave2.size = MAX_RANGE - FUSE_RANGE
        wave2.setLifetime(1f)
        wave2.fadeInSize(1f)
        wave2.intensity = -300f
        wave2.fadeOutIntensity(1f)
        DistortionShader.addDistortion(wave2)

        //add slow buff
        for (s in CombatUtils.getShipsWithinRange(m.location, MAX_RANGE)) {
          //engine.addFloatingText(engine.getPlayerShip().getMouseTarget(),s.getName() + "",20f,new Color(100,100,100,100),engine.getPlayerShip(),1f,5f);
          if (s.isAlive && s.owner != m.owner) {
            //create debuff effect
            aEP_BuffEffect.addThisBuff(s, YXOnHit(3f, 1f, s, true, 1f, ACCELERATE_MOD, SLOW_PERCENT, m.location, MAX_RANGE))
          }
        }
        //loc, vel, color, size, duration
        //loc, vel, color, size, duration
        engine!!.spawnExplosion(m.location, Vector2f(0f, 0f), EXPLOSION, 500f, 0.5f)
        val spec = DamagingExplosionSpec(
          0.2f,
          FUSE_RANGE + 300f,
          FUSE_RANGE,
          m.damageAmount,
          m.damageAmount / 4,
          CollisionClass.MISSILE_NO_FF, CollisionClass.MISSILE_NO_FF,
          0f, 0f, 0f, 0,
          EXPLOSION, EXPLOSION
        )
        spec.damageType = m.damage.type
        engine!!.spawnDamagingExplosion(spec, m.weapon.ship, m.location)
        aEP_Tool.killMissile(m, engine)
      }
    })*/
  }

  override fun onHit(projectile: DamagingProjectileAPI?, target: CombatEntityAPI?, point: Vector2f?, shieldHit: Boolean, damageResult: ApplyDamageResultAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    projectile ?: return
    for( i in 0 until 8 ){
      val angle = i * 45f
      val loc = aEP_Tool.getExtendedLocationFromPoint(projectile.location,angle, EMP_CLOUD_RADIUS/2f)
      val cloudEntity = engine?.spawnProjectile(projectile.source,projectile.weapon,"aEP_NC_missile3",loc,angle,null) as MissileAPI
      cloudEntity.source = projectile.source
      aEP_CombatEffectPlugin.addEffect(EmpArcCloud(cloudEntity as MissileAPI,4f))
    }

  }

  class EmpArcCloud : aEP_BaseCombatEffect {
    val arcTracker = IntervalUtil(0.25f,0.25f)
    val smokeTracker = IntervalUtil(0.125f,0.125f)

    constructor(entity: CombatEntityAPI, lifeTime: Float){
      init(entity)
      this.lifeTime = lifeTime

      val engine = Global.getCombatEngine()
      val spec = DamagingExplosionSpec(1f, EMP_CLOUD_RADIUS, EMP_CLOUD_RADIUS/2f,
        100f,50f,
        CollisionClass.HITS_SHIPS_AND_ASTEROIDS, CollisionClass.HITS_SHIPS_AND_ASTEROIDS,
        20f,10f,3f,0, CLOUD_COLOR, CLOUD_COLOR)
      spec.isUseDetailedExplosion = true
      spec.detailedExplosionFlashDuration = 0.3f
      spec.detailedExplosionFlashRadius = 200f
      spec.detailedExplosionRadius = EMP_CLOUD_RADIUS
      engine.spawnDamagingExplosion(spec,(entity as MissileAPI).source,entity.location)
    }

    override fun advanceImpl(amount: Float) {
      val engine = Global.getCombatEngine()
      val mine = entity as MissileAPI
      val effectiveLevel = time/lifeTime;
      val nowSize = EMP_CLOUD_RADIUS * EMP_CLOUD_END_RADIUS_MULT * effectiveLevel

      smokeTracker.advance(amount)
      arcTracker.advance(amount)
      if(smokeTracker.intervalElapsed()){
        engine.addNebulaParticle(
          mine.location, aEP_ID.VECTOR2F_ZERO,
          nowSize, 2f,
          0.25f,0.25f,1.5f, CLOUD_COLOR)
      }

      if(arcTracker.intervalElapsed()){
        val weightedPicker = WeightedRandomPicker<CombatEntityAPI>()

        for(e in CombatUtils.getEntitiesWithinRange(mine.location,nowSize/2f)){
          if(e.collisionClass == CollisionClass.NONE) continue
          if(e is MissileAPI){
            weightedPicker.add(e,1f)
            continue
          } else if (e is ShipAPI){
            if(!e.isAlive || e.isHulk) continue
            if(e.isFighter) weightedPicker.add(e,10f)
            else weightedPicker.add(e,100f)
          }
        }

        val target = weightedPicker.pickAndRemove()?: return
        mine.source?: return

        //穿盾emp的source不可为空
        engine.spawnEmpArcPierceShields(mine.source,
          MathUtils.getRandomPointInCircle(Vector2f(mine.location), nowSize/2f ),
          null,
          target,
          DamageType.ENERGY, 10f,EMP_PER_HIT,
          target.collisionRadius + nowSize/2f,//这是电弧的最大长度，如果电弧发出点在船头，想电船尾电不到就会点空气
          "tachyon_lance_emp_impact",12f,
          ARC_COLOR_FRINGE,
          ARC_COLOR)
      }

    }
  }
}

//狙击榴弹炮
class aEP_high_speed_HE_shot : Effect(){
  override fun onFire(projectile: DamagingProjectileAPI?, weapon: WeaponAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    projectile?: return
    //engine.addFloatingText(engine.getPlayerShip().getMouseTarget(), "OK", 20f, new Color(100, 100, 100, 100), engine.getPlayerShip(), 1f, 5f);
    addEffect(SplitTrigger(projectile))
  }

  class SplitTrigger : aEP_BaseCombatEffect{
    private val fuseRange = 60f
    private val triggerPointRange = 80f
    private val spreadAngle = 15f
    private val splitNum = 6
    private val earliestSplitTime = 0.35f
    val projectile: DamagingProjectileAPI
    var timer = 0f
    constructor(projectile: DamagingProjectileAPI){
      this.projectile = projectile
      init(projectile)
    }

    override fun advanceImpl(amount: Float) {
      if(projectile.isFading){
        cleanup()
        return
      }

      timer += amount
      if(timer < earliestSplitTime){
        return
      }

      var shouldSplit = false
      val triggerPoint = aEP_Tool.getExtendedLocationFromPoint(projectile.location, projectile.facing ,triggerPointRange)
      //aEP_Tool.addDebugPoint(triggerPoint)
      //检测距离为弹体前方一圆形，任何一个船体碰撞点处于圆内时触发引信
      for(ship in CombatUtils.getShipsWithinRange(triggerPoint, fuseRange)){
        if(ship.collisionClass == CollisionClass.NONE) continue
        if(ship.exactBounds == null) continue
        if(ship == projectile.source) continue
        if(ship.owner == projectile.source.owner) continue
        //aEP_Tool.addDebugText("did", ship.location)
        if(ship.shield != null && ship.shield.isOn){
          if(MathUtils.getDistance(triggerPoint, ship.location) < triggerPointRange + ship.shield.radius){
            if(Math.abs(MathUtils.getShortestRotation(VectorUtils.getAngle(ship.shield.location, triggerPoint), ship.shield.facing)) < ship.shield.activeArc/2f){
              shouldSplit = true
              //aEP_Tool.addDebugText("in", triggerPoint)
              break
            }
          }
        }else{
          for(seg in ship.exactBounds.segments){
            if(CollisionUtils.getCollides(seg.p1, seg.p2, triggerPoint, triggerPointRange)){
              shouldSplit = true
              //aEP_Tool.addDebugText("in", triggerPoint)
              break
            }
          }
        }
      }

      if(shouldSplit){
        //分裂
        var i = 0
        var angle = -spreadAngle/2f
        var angleIncreasePerLoop = spreadAngle/(splitNum.toFloat())
        while (i < splitNum){
          val newProj = Global.getCombatEngine().spawnProjectile(projectile.source,
          projectile.weapon,
          "aEP_high_speed_HE2",
          projectile.location,
          projectile.facing + angle,
          null)
          newProj.velocity.set(VectorUtils.rotate(newProj.velocity,angle))
          newProj.velocity.scale(MathUtils.getRandomNumberInRange(1f,1.5f))
          i ++
          angle += angleIncreasePerLoop
        }

        //分裂的特效
        aEP_Tool.spawnSingleCompositeSmoke(projectile.location,75f,1.5f, Color(240,240,240,125))

        //音效
        Global.getSoundPlayer().playSound("aEP_high_speed_HE_flak",1f,0.75f,projectile.location,Vector2f(0f,0f))
        Global.getCombatEngine().removeEntity(projectile)
        cleanup()
      }

    }
  }
}

//对流 温跃层主炮 无光层加速器
class aEP_duiliu_main_shot : Effect(){
  override fun onFire(projectile: DamagingProjectileAPI?, weapon: WeaponAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    //create bright spark at center
    engine!!.addSmoothParticle(
      projectile!!.location,
      Vector2f(0f, 0f),  //velocity
      400f,
      2f,  // brightness
      0.12f,  //particle live time
      Color(250, 200, 200, 250)
    )

    //create bright spark at center
    engine.addSmoothParticle(
      projectile.location,
      Vector2f(0f, 0f),  //velocity
      200f,
      2f,  // brightness
      0.25f,  //particle live time
      Color(255, 255, 255, 255)
    )

    //create spark at center
    engine.addSmoothParticle(
      projectile.location,
      Vector2f(0f, 0f),  //velocity
      1000f,
      1f,  // brightness
      0.15f,  //particle live time
      Color(250, 100, 100, 150)
    )

    val ship = weapon?.ship?: return
    //move deco weapon
    for (w in ship.getAllWeapons()) {
      if (w.slot.id.contains("RW")) {
        if (w.slot.id.contains("01")) {
          (w.effectPlugin as aEP_DecoAnimation).setGlowEffectiveLevel(0.51f)
          (w.effectPlugin as aEP_DecoAnimation).setGlowToLevel(0f)
          MagicLensFlare.createSharpFlare(engine, ship, w.location, 8f, 450f, w.currAngle - 90, Color(250, 50, 50, 31), Color(250, 100, 100, 1))
        } else if (w.slot.id.contains("02")) {
          (w.effectPlugin as aEP_DecoAnimation).setGlowEffectiveLevel(0.58f)
          (w.effectPlugin as aEP_DecoAnimation).setGlowToLevel(0f)
          MagicLensFlare.createSharpFlare(engine, ship, w.location, 12f, 480f, w.currAngle - 90, Color(250, 50, 50, 40), Color(250, 100, 100, 1))
        } else if (w.slot.id.contains("03")) {
          (w.effectPlugin as aEP_DecoAnimation).setGlowEffectiveLevel(0.65f)
          (w.effectPlugin as aEP_DecoAnimation).setGlowToLevel(0f)
          MagicLensFlare.createSharpFlare(engine, ship, w.location, 15f, 500f, w.currAngle - 90, Color(250, 50, 50, 50), Color(250, 100, 100, 1))
        } else if (w.slot.id.contains("04")) {
          (w.effectPlugin as aEP_DecoAnimation).setGlowEffectiveLevel(0.72f)
          (w.effectPlugin as aEP_DecoAnimation).setGlowToLevel(0f)
          MagicLensFlare.createSharpFlare(engine, ship, w.location, 18f, 600f, w.currAngle - 90, Color(250, 50, 50, 60), Color(250, 100, 100, 1))
        } else if (w.slot.id.contains("05")) {
          (w.effectPlugin as aEP_DecoAnimation).setGlowEffectiveLevel(0.79f)
          (w.effectPlugin as aEP_DecoAnimation).setGlowToLevel(0f)
          MagicLensFlare.createSharpFlare(engine, ship, w.location, 21f, 600f, w.currAngle - 90, Color(250, 50, 50, 71), Color(250, 100, 100, 41))
        } else if (w.slot.id.contains("06")) {
          (w.effectPlugin as aEP_DecoAnimation).setGlowEffectiveLevel(0.86f)
          (w.effectPlugin as aEP_DecoAnimation).setGlowToLevel(0f)
          MagicLensFlare.createSharpFlare(engine, ship, w.location, 24f, 720f, w.currAngle - 90, Color(250, 50, 50, 90), Color(250, 100, 100, 51))
        } else if (w.slot.id.contains("07")) {
          (w.effectPlugin as aEP_DecoAnimation).setGlowEffectiveLevel(0.93f)
          (w.effectPlugin as aEP_DecoAnimation).setGlowToLevel(0f)
          MagicLensFlare.createSharpFlare(engine, ship, w.location, 27f, 750f, w.currAngle - 90, Color(250, 50, 50, 90), Color(250, 100, 100, 60))
        } else if (w.slot.id.contains("08")) {
          (w.effectPlugin as aEP_DecoAnimation).setGlowEffectiveLevel(1f)
          (w.effectPlugin as aEP_DecoAnimation).setGlowToLevel(0f)
          MagicLensFlare.createSharpFlare(engine, ship, w.location, 30f, 800f, w.currAngle - 90, Color(250, 50, 50, 100), Color(250, 100, 100, 80))
        }
        val glowLevel = (w.effectPlugin as aEP_DecoAnimation).getDecoGlowController().effectiveLevel
        if (glowLevel < 0.1) continue
        //add glow
        val light = StandardLight(w.slot.computePosition(ship), Vector2f(0f, 0f), Vector2f(0f, 0f), null)
        light.intensity = MathUtils.clamp(glowLevel, 0f, 1f)
        light.size = 40f
        light.setColor(Color(250, 50, 50, (250 * glowLevel).toInt()))
        light.fadeIn(0f)
        light.setLifetime(1f)
        light.autoFadeOutTime = 1f
        LightShader.addLight(light)
        addEffect(aEP_AnchorStandardLight(light, ship, 1f))
      }
    }
  }
  override fun onHit(projectile: DamagingProjectileAPI?, target: CombatEntityAPI?, point: Vector2f?, shieldHit: Boolean, damageResult: ApplyDamageResultAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    val IMPULSE = 25000f
    point ?: return
    target ?: return
    aEP_Tool.applyImpulse(target, point, projectile!!.facing,IMPULSE)
    if (shieldHit) {
      val onHit = aEP_StickOnHit(
        8.1f,
        target,
        point,
        "weapons.DL_pike_shot_inShield",
        "weapons.DL_pike_shot",
        projectile.facing,
        true)
      onHit.sprite.setSize(10f,50f)
      addEffect(onHit)
    } else {
      val onHit = aEP_StickOnHit(
        6.1f,
        target,
        point,
        "weapons.DL_pike_shot_inHull",
        "weapons.DL_pike_shot",
        projectile.facing,
        false)
      onHit.sprite.setSize(10f,50f)
      addEffect(onHit)
    }
  }
}

//古斯塔夫
class aEP_RW_shot : Effect(){
  val MAX_STACK = 2f
  val DAMAGE_TO_UPKEEP_INCREASE = 500f
  val BUFF_LIFETIME = 3f

  override fun onFire(projectile: DamagingProjectileAPI?, weapon: WeaponAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    val color = Color(240, 240, 240, 240)
    projectile?:return
    weapon?: return
    var ms = aEP_MovingSprite(
      projectile.location,
      aEP_Tool.speed2Velocity(weapon.currAngle, 150f),
      0f,
      weapon.currAngle - 90f,
      0f,
      0f,
      0.42f,
      Vector2f(1600f, 400f),
      Vector2f(24f, 6f),
      "aEP_FX.ring",
      color)
    ms.setInitVel(weapon.ship.velocity )
    addEffect(ms)

    ms = aEP_MovingSprite(
      projectile.getLocation(),
      aEP_Tool.speed2Velocity(weapon.currAngle, 150f),
      0f,
      weapon.currAngle - 90f,
      0f,
      0f,
      0.35f,
      Vector2f(2400f, 600f),
      Vector2f(24f, 6f),
      "aEP_FX.ring",
      color)
    ms.setInitVel(weapon.ship.velocity)
    addEffect(ms)

    val param = aEP_Tool.FiringSmokeParam()
    param.smokeSize = 30f
    param.smokeEndSizeMult = 2f
    param.smokeSpread = 30f
    param.maxSpreadRange = 45f

    param.smokeInitSpeed = 300f
    param.smokeStopSpeed = 0.85f

    param.smokeTime = 2.25f
    param.smokeNum = 50
    param.smokeAlpha = 0.1f
    firingSmoke(weapon.getFirePoint(0),weapon.currAngle,param, weapon.ship)

    val smokeTrail = aEP_SmokeTrail(projectile,20f,1.65f,20f,20f,Color(240,240,240,120))
    smokeTrail.smokeSpreadAngleTracker.speed = 0f
    smokeTrail.smokeSpreadAngleTracker.max = 0f
    smokeTrail.smokeSpreadAngleTracker.min = 0f
    addEffect(smokeTrail)

    //create side SmokeFire
    createFanSmoke(projectile.getSpawnLocation(), aEP_Tool.angleAdd(weapon.currAngle, 90f), weapon!!.ship)
    createFanSmoke(projectile.getSpawnLocation(), aEP_Tool.angleAdd(weapon.currAngle, -90f), weapon!!.ship)

    //adjust damage due to slot type and add listener if it is not a ballistic slot
    if (weapon!!.slot.weaponType == WeaponAPI.WeaponType.BALLISTIC) {
      projectile.getDamage().setDamage(projectile.getDamage().getDamage() / 2f)
      projectile.getDamage().setType(DamageType.KINETIC)
    } else {
      if (!weapon!!.ship.hasListenerOfClass(DamageDealMult::class.java)) {
        weapon!!.ship.addListener(DamageDealMult(weapon!!.ship))
      }
    }

    //apply impulse
    aEP_Tool.applyImpulse(weapon!!.ship, weapon!!.location, weapon!!.currAngle - 180f, 5000f)
  }

  override fun onHit(projectile: DamagingProjectileAPI?, target: CombatEntityAPI?, point: Vector2f?, shieldHit: Boolean, damageResult: ApplyDamageResultAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    engine?: return
    point?: return
    val projId = projectile?.projectileSpecId?: return
    val ship = projectile.source?: return
    val weapon = projectile.weapon?: return
    //Global.getCombatEngine().addFloatingText(projectile.getLocation(),projectile.getDamageAmount() + "", 20f ,new Color(100,100,100,100),projectile, 0.25f, 120f);
    var hitColor = Color(240, 120, 50, 200)

    if (weapon.slot.weaponType != WeaponAPI.WeaponType.BALLISTIC) {
      hitColor = Color(50, 120, 240, 200)
    }

    when (projId) {
      "aEP_RW_shot" -> if (target is ShipAPI) {
        //create sparks
        var num = 1
        while (num <= 8) {
          var onHitAngle = VectorUtils.getAngle(target.getLocation(), point)
          onHitAngle += MathUtils.getRandomNumberInRange(-60, 60)
          val speed = MathUtils.getRandomNumberInRange(100, 200).toFloat()
          val lifeTime = MathUtils.getRandomNumberInRange(1f, 3f)
          addEffect(Spark(point, aEP_Tool.speed2Velocity(onHitAngle, speed), aEP_Tool.speed2Velocity(onHitAngle - 180f, speed / lifeTime), lifeTime))
          num ++
        }

        //create smokes
        num = 1
        while (num <= 28) {
          val loc = MathUtils.getRandomPointInCircle(point, 300f)
          val sizeGrowth = MathUtils.getRandomNumberInRange(0, 100).toFloat()
          val sizeAtMin = MathUtils.getRandomNumberInRange(100, 400).toFloat()
          val moveSpeed = MathUtils.getRandomNumberInRange(50, 100).toFloat()
          val ms = aEP_MovingSmoke(loc)
          ms.setInitVel(aEP_Tool.speed2Velocity(VectorUtils.getAngle(point, loc), moveSpeed))
          ms.lifeTime = 3f
          ms.fadeIn = 0f
          ms.fadeOut = 1f
          ms.size = sizeAtMin
          ms.sizeChangeSpeed = sizeGrowth
          ms.color = Color(100, 100, 100, MathUtils.getRandomNumberInRange(80, 180))
          addEffect(ms)
          num ++
        }

        //create explode
        engine!!.spawnExplosion(
          point,  //color
          Vector2f(0f, 0f),  //vel
          hitColor,  //color
          400f,  //size
          1f
        ) //duration
        engine.addNegativeParticle(
          point,
          Vector2f(0f, 0f),
          200f,
          0.5f,
          0.5f,
          Color(240, 240, 240, 200)
        )
        engine.addSmoothParticle(
          point,
          Vector2f(0f, 0f),
          300f,
          0.5f,
          0.2f,
          hitColor
        )

        //add on hit effect for ballistic slot
        if (weapon.slot.weaponType == WeaponAPI.WeaponType.BALLISTIC) {
          val toUseWeaponId = "aEP_railway_gun_shot"
          val pro1 = engine.spawnProjectile(
            ship,  //source ship
            weapon,  //source weapon,
            toUseWeaponId,  //whose proj to be use
            aEP_Tool.getExtendedLocationFromPoint(point, projectile.facing, -(Global.getSettings().getWeaponSpec(toUseWeaponId).projectileSpec as ProjectileSpecAPI).getMoveSpeed(null, null) * 0.25f),  //loc
            projectile.facing,  //facing
            null
          )
          val damage1 = (pro1 as DamagingProjectileAPI).damage
          damage1.damage = projectile.damage.damage
          damage1.type = DamageType.HIGH_EXPLOSIVE
          engine.removeEntity(projectile)
        }

        //add on hit effect for non-ballistic slot
        if (weapon.slot.weaponType != WeaponAPI.WeaponType.BALLISTIC) {
          aEP_BuffEffect.addThisBuff(target, aEP_UpKeepIncrease(BUFF_LIFETIME, target as ShipAPI?, false, MAX_STACK, DAMAGE_TO_UPKEEP_INCREASE, "aEP_RWOnHit"))
        }
      }
    }

    //play sound
    Global.getSoundPlayer().playSound(
      "aEP_RW_hit",
      1f, 2f,  // pitch,volume
      weapon.location,  //location
      Vector2f(0f, 0f)
    ) //velocity

    //add hit glow if it pass through missiles
    engine.addHitParticle(
      point,
      Vector2f(0f, 0f),
      400f,
      1f,
      1f,
      hitColor
    )
  }

  fun createFanSmoke(loc: Vector2f, facing: Float, ship: ShipAPI) {
    val glow = Color(250, 250, 250, 250)
    val glowSize = 300f
    val centerGlow = Color(250, 250, 250, 250)
    val centerGlowSize = 100f
    val glowTime = 0.5f

    //add glow
    val light = StandardLight(loc, Vector2f(0f, 0f), Vector2f(0f, 0f), null)
    light.intensity = MathUtils.clamp(glow.alpha / 250f, 0f, 1f)
    light.size = glowSize
    light.setColor(glow)
    light.fadeIn(0f)
    light.setLifetime(glowTime / 2f)
    light.autoFadeOutTime = glowTime / 2f
    LightShader.addLight(light)

    //add center glow
    Global.getCombatEngine().addSmoothParticle(
      loc,  //location
      Vector2f(0f, 0f),  //velocity
      (centerGlowSize + MathUtils.getRandomNumberInRange(0f, centerGlowSize)) * 2f / 3f,  //size
      1f,  //brightness
      glowTime,  //lifetime
      centerGlow
    ) //color


    val param = aEP_Tool.FiringSmokeParam()
    param.smokeSize = 30f
    param.smokeEndSizeMult = 1.5f
    param.smokeSpread = 45f
    param.maxSpreadRange = 50f
    param.smokeInitSpeed = 160f
    param.smokeStopSpeed = 0.6f
    param.smokeTime = 2f
    param.smokeNum = 20
    param.smokeAlpha = 0.2f
    firingSmoke(loc,facing,param, ship)
  }

  internal class DamageDealMult(var ship: ShipAPI) : DamageDealtModifier {
    override fun modifyDamageDealt(param: Any?, target: CombatEntityAPI, damage: DamageAPI, point: Vector2f, shieldHit: Boolean): String? {
      if (param is DamagingProjectileAPI && shieldHit) {
        val proj = param
        if (proj.projectileSpecId != null && proj.projectileSpecId == "aEP_RW_shot") {
          if (proj.weapon != null && proj.weapon.slot.weaponType != WeaponAPI.WeaponType.BALLISTIC) {
            damage.isSoftFlux = true
          }
        }
      }
      return null
    }

  }

  internal class Spark(var location: Vector2f, velocity: Vector2f, acceleration: Vector2f, lifeTime: Float) : aEP_BaseCombatEffect() {
    var velocity: Vector2f
    var acceleration = Vector2f(0f, 0f)
    var size = 30f
    var sparkColor = Color(200, 200, 50, 200)
    var lastFrameLoc: Vector2f

    var smokeTracker = IntervalUtil(0.2f,0.2f)
    override fun advance(amount: Float) {
      super.advance(amount)
      val percent = time/lifeTime
      Global.getCombatEngine().addSmoothParticle(
        location,
        Vector2f(0f, 0f),
        size * percent,
        1f,
        amount * 2f,
        Color(sparkColor.red, sparkColor.green, sparkColor.blue, (-200 * percent).toInt() + 250)
      )
      location = Vector2f(location.x + velocity.x * amount, location.y + velocity.y * amount)
      velocity = Vector2f(velocity.x + acceleration.x * amount, velocity.y + acceleration.y * amount)
      smokeTracker.advance(amount)
      if (smokeTracker.intervalElapsed()) {
        val maxDistWithoutSmoke = 30f
        val moveDist = 30f
        val lifeTime = 1f
        val smokeMinSize = 5f
        val smokeMaxSize = 25f
        val endSmokeSize = 30f

        //Global.getCombatEngine().addFloatingText(m.getLocation(),movedDistLastFrame + "", 20f ,new Color(100,100,100,100),m, 0.25f, 120f);
        var movedDistLastFrame = MathUtils.getDistance(lastFrameLoc, location)
        var num = 1
        while (movedDistLastFrame >= 0f) {
          val smokeSize = MathUtils.getRandomNumberInRange(smokeMinSize, smokeMaxSize)
          val sizeChange = (endSmokeSize - smokeSize) / lifeTime
          val engineFacing = aEP_Tool.angleAdd(aEP_Tool.velocity2Speed(velocity).x, 180f)
          val smokeLoc = aEP_Tool.getExtendedLocationFromPoint(location, engineFacing, num * maxDistWithoutSmoke)
          val smoke = aEP_MovingSmoke(smokeLoc) // spawn location
          smoke.setInitVel(aEP_Tool.speed2Velocity(engineFacing + MathUtils.getRandomNumberInRange(-30f, 30f), moveDist / lifeTime))
          smoke.fadeIn = 0f
          smoke.fadeOut = 0.35f
          smoke.lifeTime = lifeTime
          smoke.size = smokeSize
          smoke.sizeChangeSpeed = sizeChange
          smoke.color = Color(120, 120, 120, 150)
          addEffect(smoke)

          //Global.getCombatEngine().addFloatingText(m.getLocation(),movedDistLastFrame + "", 20f ,new Color(100,100,100,100),m, 0.25f, 120f);
          movedDistLastFrame = movedDistLastFrame - maxDistWithoutSmoke
          num = num + 1
        }
        lastFrameLoc = Vector2f(location.x, location.y)
      }
    }

    init {
      lastFrameLoc = location
      this.velocity = velocity
      this.acceleration = acceleration
      this.lifeTime = lifeTime
    }
  }

}

//EMP钉矛
class aEP_EMP_pike_shot : Effect(){
  companion object{
    //质量参考
    //驱逐400-500
    //巡洋1200-2200
    //战列2500-4000
    val MAX_SPEED_REDUCE = 0.35f //
    val MIN_SPEED_REDUCE = 0.05f //
    val SPEED_REDUCE_BY_MASS = 100f // (SPEED_REDUCE_BY_MASS * SPEED_REDUCE_BY_MASS) / (mass * SPEED_REDUCE_BY_MASS) = speed reduce
    val EMP_DAMAGE = 30f
    val DEBUFF_TIME = 1f //
    val ACCELERATE_MOD = 0.75f //by mult
  }

  override fun onHit(projectile: DamagingProjectileAPI?, target: CombatEntityAPI?, point: Vector2f?, shieldHit: Boolean, damageResult: ApplyDamageResultAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    point?: return
    projectile?: return
    target?: return
    if (shieldHit) {
      addEffect(EMPEffect(projectile.weapon, 4.5f, target, point, "weapons.EMP_pike_shot_inShield", projectile.facing, true))
    } else {
      addEffect(EMPEffect(projectile.weapon, 4.5f, target, point, "weapons.EMP_pike_shot_inHull", projectile.facing, false))
    }
  }

  internal class EMPEffect : aEP_StickOnHit {

    var source: WeaponAPI
    var empTracker = IntervalUtil(1f,1f)
    constructor(
      weapon: WeaponAPI,
      duration: Float,
      target: CombatEntityAPI,
      point: Vector2f,
      spriteId: String,
      OnHitAngle: Float,
      hitShield: Boolean) : super(duration, target, point, spriteId,"weapons.EMP_pike_shot",OnHitAngle, hitShield) {
      this.source = weapon
    }

    override fun advanceImpl(amount: Float) {
      empTracker.advance(amount)
      if (!empTracker.intervalElapsed()) return
      val speedMult = 1 - aEP_Tool.limitToTop((SPEED_REDUCE_BY_MASS * SPEED_REDUCE_BY_MASS )/ (target.mass + SPEED_REDUCE_BY_MASS), MAX_SPEED_REDUCE, MIN_SPEED_REDUCE)
      target.velocity.scale(speedMult)
      target.angularVelocity *= speedMult
      aEP_BuffEffect.addThisBuff(target, EMPPikeOnHit(DEBUFF_TIME, 1f, target, true, 1f, ACCELERATE_MOD))
      Global.getCombatEngine().spawnEmpArcPierceShields(
        source.ship,  //ShipAPI damageSource,
        renderLoc,  // Vector2f point,
        target,  // CombatEntityAPI pointAnchor,
        target,  // CombatEntityAPI empTargetEntity,
        DamageType.ENERGY,  // DamageType damageType,
        0f,  // float damAmount,
        EMP_DAMAGE,  // float empDamAmount,
        3000f,  // float maxRange,
        null,  // java.lang.String impactSoundId,
        15f,  // float thickness,
        Color(100, 100, 100, 150),  // java.awt.Color fringe,
        Color(100, 50, 50, 100)
      ) // java.awt.Color core)
    }

  }

  internal class EMPPikeOnHit(lifeTime: Float, initStack: Float, entity: CombatEntityAPI, isRenew: Boolean, maxStack: Float, accelerateMod: Float) : aEP_Buff() {
    override fun play() {
      if (entity is ShipAPI) {
        val percent = 1 - time/lifeTime
        (entity as ShipAPI).mutableStats.acceleration.modifyMult("aEP_EMPPikeOnHit", 1 - ACCELERATE_MOD*percent)
      }
    }

    override fun readyToEnd() {
      if (entity is ShipAPI) {
        (entity as ShipAPI).mutableStats.acceleration.unmodify("aEP_EMPPikeOnHit")
      }
    }

    init {
      this.lifeTime = lifeTime
      this.stackNum = initStack
      this.entity = entity
      this.isRenew = isRenew
      this.maxStack = maxStack
      this.buffType = "EMPPikeOnHit"
    }
  }

}

//转膛炮系列
class aEP_KF_shot : Effect(){
  override fun onFire(projectile: DamagingProjectileAPI?, weapon: WeaponAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    weapon?:return
    val param = aEP_Tool.FiringSmokeParam()
    param.smokeSize = 25f
    param.smokeEndSizeMult = 1.5f
    param.smokeSpread = 20f
    param.maxSpreadRange = 40f

    param.smokeInitSpeed = 100f
    param.smokeStopSpeed = 0.9f

    param.smokeTime = 1.5f
    param.smokeNum = 5
    param.smokeAlpha = 0.2f

    firingSmoke(projectile?.location?: Vector2f(0f,0f),weapon.currAngle,param, weapon.ship)

    val param2 = aEP_Tool.FiringSmokeParam()
    param2.smokeSize = 25f
    param2.smokeEndSizeMult = 1.5f

    param2.smokeSpread = 10f
    param2.maxSpreadRange = 20f

    param2.smokeInitSpeed = 100f
    param2.smokeStopSpeed = 0.9f

    param2.smokeTime = 0.5f
    param2.smokeNum = 5
    param2.smokeAlpha = 0.1f

    param2.smokeColor = Color(240,110,20)

    firingSmoke(projectile?.location?: Vector2f(0f,0f),weapon.currAngle,param2, weapon.ship)

    val param3 = aEP_Tool.FiringSmokeParam()
    param3.smokeSize = 25f
    param3.smokeEndSizeMult = 1.5f

    param3.smokeSpread = 5f
    param3.maxSpreadRange = 10f

    param3.smokeInitSpeed = 100f
    param3.smokeStopSpeed = 0.9f

    param3.smokeTime = 0.3f
    param3.smokeNum = 5
    param3.smokeAlpha = 0.2f

    param3.smokeColor = Color(255,240,160)

    firingSmokeNebula(projectile?.location?: Vector2f(0f,0f),weapon.currAngle,param3, weapon.ship)

  }

  override fun onHit(projectile: DamagingProjectileAPI?, target: CombatEntityAPI?, point: Vector2f?, shieldHit: Boolean, damageResult: ApplyDamageResultAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    spawnSingleCompositeSmoke(point?: Vector2f(0f,0f),100f,0.5f,Color(240,240,240,180))
  }

  override fun onExplosion(explosion: DamagingProjectileAPI?, originalProjectile: DamagingProjectileAPI?, weaponId: String?) {
    spawnCompositeSmoke(explosion?.location?: Vector2f(0f,0f),100f,0.5f,Color(240,240,240,180))
  }
}

class aEP_KF_large_shot : Effect(){
  override fun onFire(projectile: DamagingProjectileAPI?, weapon: WeaponAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    weapon?:return
    val param = aEP_Tool.FiringSmokeParam()
    param.smokeSize = 25f
    param.smokeEndSizeMult = 1.5f
    param.smokeSpread = 20f
    param.maxSpreadRange = 40f

    param.smokeInitSpeed = 100f
    param.smokeStopSpeed = 0.9f

    param.smokeTime = 1.5f
    param.smokeNum = 5
    param.smokeAlpha = 0.2f

    firingSmoke(projectile?.location?: Vector2f(0f,0f),weapon.currAngle,param, weapon.ship)

    val param2 = aEP_Tool.FiringSmokeParam()
    param2.smokeSize = 25f
    param2.smokeEndSizeMult = 1.5f

    param2.smokeSpread = 10f
    param2.maxSpreadRange = 20f

    param2.smokeInitSpeed = 100f
    param2.smokeStopSpeed = 0.9f

    param2.smokeTime = 0.5f
    param2.smokeNum = 5
    param2.smokeAlpha = 0.1f

    param2.smokeColor = Color(240,110,20)

    firingSmoke(projectile?.location?: Vector2f(0f,0f),weapon.currAngle,param2, weapon.ship)

    val param3 = aEP_Tool.FiringSmokeParam()
    param3.smokeSize = 25f
    param3.smokeEndSizeMult = 1.5f

    param3.smokeSpread = 5f
    param3.maxSpreadRange = 10f

    param3.smokeInitSpeed = 100f
    param3.smokeStopSpeed = 0.9f

    param3.smokeTime = 0.3f
    param3.smokeNum = 5
    param3.smokeAlpha = 0.2f

    param3.smokeColor = Color(255,240,160)

    firingSmokeNebula(projectile?.location?: Vector2f(0f,0f),weapon.currAngle,param3, weapon.ship)

    //创造蛋壳
    projectile?: return
    var onLeft = false
    if(MathUtils.getShortestRotation(VectorUtils.getAngle(weapon.location,projectile.location),weapon.currAngle) > 0){
      onLeft = true
    }
    if(onLeft){
      val ejectPoint = aEP_Tool.getExtendedLocationFromPoint(weapon.location,weapon.currAngle-100f,20f)
      val ms = aEP_MovingSprite(ejectPoint, Vector2f(6f,3f),weapon.currAngle,"graphics/weapons/aEP_large_kinetic_flak/shred.png")
      ms.lifeTime = 2f
      ms.fadeIn = 0.05f
      ms.fadeOut = 0.2f
      ms.color = Color(255,255,255,255)
      ms.angle = weapon.currAngle
      ms.angleSpeed = MathUtils.getRandomNumberInRange(-180f,180f)
      ms.setInitVel(aEP_Tool.speed2Velocity(weapon.currAngle-90f +MathUtils.getRandomNumberInRange(-10f,10f),MathUtils.getRandomNumberInRange(40f,80f)))
      ms.setInitVel(weapon.ship.velocity)
      ms.stopSpeed = 0.9f
      addEffect(ms)
    }else{
      val ejectPoint = aEP_Tool.getExtendedLocationFromPoint(weapon.location,weapon.currAngle+100f,20f)
      val ms = aEP_MovingSprite(ejectPoint, Vector2f(6f,3f),weapon.currAngle,"graphics/weapons/aEP_large_kinetic_flak/shred.png")
      ms.lifeTime = 2f
      ms.fadeIn = 0.05f
      ms.fadeOut = 0.2f
      ms.color = Color(255,255,255,255)
      ms.angle = weapon.currAngle
      ms.angleSpeed = MathUtils.getRandomNumberInRange(-180f,180f)
      ms.setInitVel(aEP_Tool.speed2Velocity(weapon.currAngle+90f + MathUtils.getRandomNumberInRange(-10f,10f),MathUtils.getRandomNumberInRange(40f,80f)))
      ms.setInitVel(weapon.ship.velocity)
      ms.stopSpeed = 0.9f
      addEffect(ms)
    }

  }

  override fun onHit(projectile: DamagingProjectileAPI?, target: CombatEntityAPI?, point: Vector2f?, shieldHit: Boolean, damageResult: ApplyDamageResultAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    //spawnCompositeSmoke(point?: Vector2f(0f,0f),100f,0.5f,Color(240,240,240,150))
  }

  override fun onExplosion(explosion: DamagingProjectileAPI?, originalProjectile: DamagingProjectileAPI?, weaponId: String?) {
    explosion?:return
    //弹片
    var angle = 0f
    var rad = 100f
    var num = 8f
    var size = 2f
    var moveSpeed = 50f
    while (angle < 360){
      val p = aEP_Tool.getExtendedLocationFromPoint(explosion.location,angle,MathUtils.getRandomNumberInRange(0f,rad/3f))
      val randomSize = MathUtils.getRandomNumberInRange(0f,2f) + size
      val vel = VectorUtils.getDirectionalVector(explosion.location, p)
      vel.scale(moveSpeed)
      val ms = aEP_MovingSprite(p, Vector2f(randomSize,randomSize),MathUtils.getRandomNumberInRange(0f,360f),"graphics/weapons/aEP_large_kinetic_flak/shell.png")
      ms.lifeTime = 2f
      ms.fadeOut = 0.8f
      ms.color = Color(240,240,200,40)
      ms.setInitVel(vel)
      ms.stopSpeed = 0.85f
      addEffect(ms)
      angle += 360f/num
    }


    //中心白色范围光
    Global.getCombatEngine().addSmoothParticle(
      explosion.location,
      Vector2f(0f, 0f),
      100f,
      1f,
      0f,
      1f,
      Color(255,255,255,120),
    )
    //中心白色爆炸正中
    Global.getCombatEngine().addSmoothParticle(
      explosion?.location,
      Vector2f(0f, 0f),
      50f,
      1f,
      0f,
      0.5f,
      Color.white
    )

    //扩散烟圈
    angle = 0f
    rad = 40f
    num = 16f
    moveSpeed = 10f
    val spread = 360f / num
    while (angle < 360) {
      val outPoint = MathUtils.getPoint(explosion?.location, rad, angle)
      val vel = VectorUtils.getDirectionalVector(explosion?.location, outPoint)
      vel.scale(moveSpeed)
      Global.getCombatEngine().addNebulaSmokeParticle(
        outPoint,
        vel,
        40f,
        1.2f,
        0.1f,
        0f,
        2f,
        Color(205, 205, 205, 15)
      )
      Global.getCombatEngine().addSwirlyNebulaParticle(
        outPoint,
        vel,
        40f,
        1.2f,
        0.1f,
        0f,
        2f,
        Color(100, 100, 100, 5),
        true
      )
      angle += spread
    }
    val centerSmoke = aEP_MovingSmoke(explosion.location)
    centerSmoke.size = 100f
    centerSmoke.lifeTime = 3f
    centerSmoke.fadeOut = 0.9f
    centerSmoke.color = Color(240,240,240,40)
    //spawnCompositeSmoke(explosion?.location?: Vector2f(0f,0f),100f,4f,Color(240,240,240,40))
  }
}

//锚点无人机模拟导弹
class aEP_maodian_drone_missile : Effect(){
  override fun onFire(projectile: DamagingProjectileAPI?, weapon: WeaponAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    projectile?.weapon?.ship?: return
    //无论如何都先移除proj
    engine?.removeEntity(projectile)

    val ship = projectile.weapon.ship
    //屏蔽左上角入场提升
    val suppressBefore = engine?.getFleetManager(projectile.owner?:100)?.isSuppressDeploymentMessages
    engine?.getFleetManager(projectile.owner?:100)?.isSuppressDeploymentMessages = true
    //生成无人机
    val drone = engine?.getFleetManager(projectile.owner?:100)?.spawnShipOrWing(
      "aEP_MaoDian_drone",
      projectile.location?: Vector2f(0f,0f),
      projectile.facing
    )
    //恢复左上角入场提示
    engine?.getFleetManager(projectile.owner?:100)?.isSuppressDeploymentMessages = suppressBefore?: false

    //旋转雷达
    if(drone != null){
      for(w in drone.allWeapons){
        if(w.spec.weaponId == "aEP_maodian_radar"){
          w.currAngle = ship.facing
        }
      }
    }
    drone?: return

    //把武器转头藏好，不要生成以后穿模
    for( w in drone.allWeapons ){
      if(w.slot.id.equals("WS0003")) w.currAngle = (drone.facing-10)
      if(w.slot.id.equals("WS0004")) w.currAngle = (drone.facing+10)
      if(w.slot.id.equals("WS0005")) w.currAngle = (drone.facing+120)
    }

    drone.velocity.set( projectile.velocity?:Vector2f(0f,0f))
    var targetLoc = ship.mouseTarget?: Vector2f(0f,0f)
    if(ship.customData["aEP_MDDroneLaunchAI_assign_target"] is Vector2f){
      targetLoc = ship.customData["aEP_MDDroneLaunchAI_assign_target"] as Vector2f
      ship.customData["aEP_MDDroneLaunchAI_assign_target"] = null
    }

    val sysRange = aEP_Tool.getSystemRange(ship,aEP_MaodianDroneLaunch.SYSTEM_RANGE)
    if(MathUtils.getDistance(targetLoc,ship.location) - ship.collisionRadius > sysRange){
      val angle = VectorUtils.getAngle(ship.location,targetLoc);
      targetLoc = Vector2f(aEP_Tool.getExtendedLocationFromPoint(ship.location,angle,sysRange+ship.collisionRadius))
    }
    //原版getShipAI返回的是ai warp，并不是ai文件
    //不能通过gatShipAI is xxx 来cast后用自己的方法
    //这里只能setAI了，先初始化，设定好了在覆盖ai
    val ai = aEP_MaoDianDroneAI(drone)
    ai.setToTarget(Vector2f(targetLoc))
    drone.shipAI = ai
    addEffect(ShowLocation(drone,ai))
  }

  class ShowLocation : aEP_BaseCombatEffect{
    val ai:aEP_MaoDianDroneAI
    constructor(drone:ShipAPI, ai: aEP_MaoDianDroneAI):super(){
      init(drone)
      this.ai = ai
    }

    override fun advanceImpl(amount: Float) {
      val sprite = Global.getSettings().getSprite("graphics/aEP_FX/noise.png")
      MagicRender.singleframe(sprite,
        ai.targetLoc?: VECTOR2F_ZERO,
        Vector2f(40f,40f),
        0f, Color(255,0,0,250),false)

      if(ai.stat is aEP_MaoDianDroneAI.HoldShield){
        cleanup()
      }
    }

    override fun render(layer: CombatEngineLayers, viewport: ViewportAPI) {
    }
  }
}

//巡洋导弹发射装置
//控制如何爆炸在FighterSpecial里面
class aEP_cruise_missile_weapon_shot : Effect(){
  override fun onFire(projectile: DamagingProjectileAPI?, weapon: WeaponAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    weapon?.ship?.fleetMember ?: return
    engine?: return
    projectile?: return
    val itemId = aEP_CruiseMissileLoadIntel.getLoadedItemId(weapon.ship.fleetMemberId)
    val hullId = aEP_CruiseMissileLoadIntel.ITEM_TO_SHIP_ID[itemId]?:""
    if(hullId.equals(""))return

    weapon.ammo = 0
    val manager = if (weapon.ship == null) {
      engine.getFleetManager(1)
    } else {
      engine.getFleetManager(weapon.ship.owner)
    }
    //保证hull和variant使用相同ids
    val ship = manager.spawnShipOrWing(hullId, projectile.location, projectile.facing, 0f)
    //把碰撞暂时改为战机，在引信插件里面发射后一段时间改回舰船
    ship.collisionClass = CollisionClass.FIGHTER
    ship.velocity.set(projectile.velocity)
    engine.removeEntity(projectile)
    if (engine.isInCampaign) {
      LOADING_MAP.keys.remove(ship.fleetMemberId)
    }
  }

}

//DG-3巨型长管链炮
class aEP_chaingun_shot2 : Effect{
  private val STRUCTURE_DAMAGE = 35f
  private val flames: MutableList<String> = ArrayList()
  var spriteNum = 0
  constructor() {
    flames.add("weapons.LBCG_flame")
    flames.add("weapons.LBCG_flame_core")
    flames.add("weapons.LBCG_flame2")
    flames.add("weapons.LBCG_flame2_core")
  }

  override fun onHit(projectile: DamagingProjectileAPI?, target: CombatEntityAPI?, point: Vector2f?, shieldHit: Boolean, damageResult: ApplyDamageResultAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    if(shieldHit) return
    if(target is ShipAPI){
      val damage = aEP_Tool.computeDamageToShip(projectile?.source, target,projectile?.weapon,STRUCTURE_DAMAGE,DamageType.FRAGMENTATION,shieldHit )
      val armorLevel = DefenseUtils.getArmorLevel(target,point)
      if(armorLevel <= 0.05f){
        //不能通过此种方式将hp减到负数或者0
        if(target.hitpoints > damage + 1) target.hitpoints -= damage
        //红色爆炸特效
        var randomVel = MathUtils.getRandomPointInCone(point,75f, angleAdd(projectile?.facing?:0f,-200f),angleAdd(projectile?.facing?:0f,160f))
        randomVel = Vector2f(randomVel.x- (point?.x?:0f),randomVel.y-(point?.y?:0f))
        engine?.spawnExplosion(point, Vector2f(target.velocity.x + randomVel.x,target.velocity.y+ randomVel.y) , Color(250,25,25,184),35f,0.5f)
        //跳红字代码伤害
        engine?.addFloatingDamageText(point,damage,Color(255,25,25,234),target,projectile?.source)
      }
    }
  }

  override fun onFire (proj: DamagingProjectileAPI?, weapon: WeaponAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    //create shell
    var side = 1f
    if (MathUtils.getShortestRotation(weapon!!.currAngle, VectorUtils.getAngle(weapon.location, proj!!.spawnLocation)) > 0) side = -1f

    //create flame
    spriteNum = spriteNum + 2
    if (spriteNum >= flames!!.size) {
      spriteNum = 0
    }
    val offset = proj.spawnLocation
    addEffect(
      aEP_MovingSprite(
        offset,  //position
        Vector2f(0f, 0f),
        0f,
        weapon.currAngle - 90f,
        0f,
        0.075f,
        0.025f,
        Vector2f(-56f, -48f),
        Vector2f(28f, 24f),
        flames[spriteNum],
        Color(200, 200, 50, 120)
      )
    )
    val color = (180 * Math.random()).toInt() + 40
    addEffect(
      aEP_MovingSprite(
        offset,  //position
        Vector2f(0f, 0f),
        0f,
        weapon.currAngle - 90f,
        0f,
        0.075f,
        0.025f,
        Vector2f(-56f, -48f),
        Vector2f(28f, 24f),
        flames[spriteNum + 1],
        Color(220, 220, color, 220)
      )
    )
    engine!!.addSmoothParticle(
      offset,
      Vector2f(0f, 0f),
      40f + MathUtils.getRandomNumberInRange(0, 40),
      1f,
      0.1f,
      Color(200, 200, 50, 60 + MathUtils.getRandomNumberInRange(0, 60))
    )
    var distFromOffset = 0f
    while (distFromOffset < 15f) {
      engine.addSmoothParticle(
        getExtendedLocationFromPoint(offset, weapon.currAngle, distFromOffset),
        Vector2f(0f, 0f),
        0.6f * (21 - distFromOffset),  //size
        1f,  //brightness
        0.075f,  //duration
        Color(200, 200, 50, 180)
      )
      distFromOffset = distFromOffset + 2f
    }


    //VEs.createCustomVisualEffect(new SmokeTrail(proj));
  }
}

//闪电机炮
class aEP_chaingun_shotAP : Effect(){
  private val MAX_STACK = 400f
  private val DAMAGE_TO_UPKEEP_INCREASE = 8f
  private val BUFF_LIFETIME = 10f
  override fun onHit(projectile: DamagingProjectileAPI?, target: CombatEntityAPI?, point: Vector2f?, shieldHit: Boolean, damageResult: ApplyDamageResultAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    if (target is ShipAPI && shieldHit) {
      //engine.addFloatingText(engine.getPlayerShip().getMouseTarget(),size +"",20f,new Color(100,100,100,100),engine.getPlayerShip(),1f,5f);
      addThisBuff(target, aEP_UpKeepIncrease(BUFF_LIFETIME, target as ShipAPI?, false, MAX_STACK,DAMAGE_TO_UPKEEP_INCREASE, "aEP_ChaingunAPOnHit"))
    }
  }
}

//深度遥控战机磁吸炸弹
class aEP_shenceng_drone_mine : Effect(){
  companion object{
    const val MAGNETIC_ATTRACTION_RANGE = 500f
    const val MAGNETIC_ATTRACTION_ACCELERATION = 1000f
  }

  override fun onFire(projectile: DamagingProjectileAPI?, weapon: WeaponAPI?, engine: CombatEngineAPI?, weaponId: String?) {
    projectile?:return
    //先把速度停下来，防止初速度太高飞走了
    projectile.velocity.scale(0.25f)
    //尝试寻找发射源（飞机）
    val fighter =  weapon?.ship
    aEP_CombatEffectPlugin.addEffect(DriftToTarget(projectile))
    //给一个随机的转动初速度，不然太呆了
    projectile.angularVelocity = MathUtils.getRandomNumberInRange(-15f,15f)
  }
  class DriftToTarget : aEP_BaseCombatEffect{
    val magneticTracker = IntervalUtil(0.1f,0.1f)
    constructor(projectile: DamagingProjectileAPI){
      init(projectile)
    }

    override fun advanceImpl(amount: Float) {
      magneticTracker.advance(amount)
      if(!magneticTracker.intervalElapsed()) return

      //寻找最近的敌方非战机舰船
      val proj = entity as DamagingProjectileAPI
      var nearestTarget = aEP_Tool.getNearestEnemyCombatShip(proj)

      //找到了，如果在400范围内，向目标飞去
      if(nearestTarget != null && MathUtils.getDistance(proj.location,nearestTarget.location) < MAGNETIC_ATTRACTION_RANGE )
        aEP_Tool.forceSetThroughPosition(proj,nearestTarget.location,amount,MAGNETIC_ATTRACTION_ACCELERATION,(proj as MissileAPI).maxSpeed)
      //否则减速原地禁止
      else
        proj.velocity.scale(0.9f)
    }
  }
}











