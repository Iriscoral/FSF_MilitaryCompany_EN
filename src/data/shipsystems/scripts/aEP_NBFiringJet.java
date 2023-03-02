package data.shipsystems.scripts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipEngineControllerAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.util.IntervalUtil;
import combat.impl.VEs.aEP_MovingSmoke;
import combat.plugin.aEP_CombatEffectPlugin;
import combat.util.aEP_Tool;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class aEP_NBFiringJet extends BaseShipSystemScript
{

  static final float MAX_SPEED_FLAT = 70f;
  static final float ACC_PERCENT = 50f;
  static final float MAX_TURN_RATE_FLAT = 6f;
  static final float MAX_TURN_RATE_PERCENT = 50f;
  static final float TURN_ACC_FLAT = 6f;
  static final float TURN_ACC_PERCENT = 50f;
  static final float WEAPON_TURN_RATE_PERCENT = 50f;

  IntervalUtil ammoReloader = new IntervalUtil(0.1f, 0.1f);
  IntervalUtil smokeTrailTimer = new IntervalUtil(0.1f, 0.1f);

  @Override
  public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
    ShipAPI ship = (ShipAPI) stats.getEntity();
    if (ship == null) return;

    ammoReloader.advance(aEP_Tool.getAmount(ship)* ((effectLevel*effectLevel) * 0.5f + 0.5f));
    if (ammoReloader.intervalElapsed())
      for (WeaponAPI w : ship.getAllWeapons())
        //ammoTracker的addOneAmmo可以突破最大弹药上限，限制一下
        if (w.getSlot().getWeaponType() == WeaponAPI.WeaponType.BUILT_IN && w.usesAmmo() && w.getAmmo() < w.getMaxAmmo())
          w.getAmmoTracker().addOneAmmo();

    ship.setJitterShields(false);
    ship.setJitterUnder(id, new Color(240, 100, 50, 200), 1, 4, 14);
    //改变速度
    stats.getMaxSpeed().modifyFlat(id, MAX_SPEED_FLAT * (effectLevel * 0.75f + 0.25f));
    stats.getAcceleration().modifyPercent(id, ACC_PERCENT);
    stats.getDeceleration().modifyPercent(id, ACC_PERCENT);
    stats.getTurnAcceleration().modifyFlat(id, TURN_ACC_FLAT);
    stats.getTurnAcceleration().modifyPercent(id, TURN_ACC_PERCENT);
    stats.getMaxTurnRate().modifyFlat(id, MAX_TURN_RATE_FLAT);
    stats.getMaxTurnRate().modifyPercent(id, MAX_TURN_RATE_PERCENT);
    stats.getWeaponTurnRateBonus().modifyPercent(id, WEAPON_TURN_RATE_PERCENT);

    //烟雾拖尾
    smokeTrailTimer.advance(aEP_Tool.getAmount(null));
    if (smokeTrailTimer.intervalElapsed())
      createSmokeTrail(ship);

    //下面的只运行一次
    if (effectLevel < 1) return;
    Vector2f toAddVel = aEP_Tool.Util.speed2Velocity(ship.getFacing(), ship.getMaxSpeed() + MAX_SPEED_FLAT);
    ship.getVelocity().set(ship.getVelocity().x + toAddVel.x, ship.getVelocity().y + toAddVel.y);
    for (ShipEngineControllerAPI.ShipEngineAPI e : ship.getEngineController().getShipEngines()) {
      Vector2f loc = e.getEngineSlot().computePosition(ship.getLocation(), ship.getFacing());
      Global.getCombatEngine().addSmoothParticle(loc,
        new Vector2f(0f, 0f),
        500f,//size
        1f,//brightness
        0.15f,//duration
        new Color(250, 120, 120, 255));

      aEP_MovingSmoke ms = new aEP_MovingSmoke(loc);
      ms.setInitVel(aEP_Tool.Util.speed2Velocity(e.getEngineSlot().computeMidArcAngle(ship.getFacing()), 25f));
      ms.setLifeTime(1.6f);
      ms.setFadeIn(0.075f);
      ms.setFadeOut(0.65f);
      ms.setSize(100);
      ms.setSizeChangeSpeed(100);
      ms.setColor(new Color(255,255,255,200));
      aEP_CombatEffectPlugin.Mod.addEffect(ms);

    }
  }

  @Override
  public void unapply(MutableShipStatsAPI stats, String id) {
    ShipAPI ship = (ShipAPI) stats.getEntity();
    if (ship == null) return;
    for (WeaponAPI w : ship.getAllWeapons())
      //ammoTracker的addOneAmmo可以突破最大弹药上限，限制一下
      if (w.getSlot().getWeaponType() == WeaponAPI.WeaponType.BUILT_IN && w.usesAmmo())
        w.getAmmoTracker().setAmmo(w.getMaxAmmo() / 2);

    stats.getMaxSpeed().unmodify(id);
    stats.getMaxTurnRate().unmodify(id);
    stats.getTurnAcceleration().unmodify(id);
    stats.getAcceleration().unmodify(id);
    stats.getDeceleration().unmodify(id);
    stats.getWeaponTurnRateBonus().unmodify(id);
  }

  void createSmokeTrail(ShipAPI ship) {
    for (ShipEngineControllerAPI.ShipEngineAPI e : ship.getEngineController().getShipEngines()) {
      Vector2f loc = e.getEngineSlot().computePosition(ship.getLocation(), ship.getFacing());

      aEP_MovingSmoke ms = new aEP_MovingSmoke(loc);
      ms.setInitVel(aEP_Tool.Util.speed2Velocity(e.getEngineSlot().computeMidArcAngle(ship.getFacing()), 35f));
      ms.setLifeTime(1f);
      ms.setFadeIn(0.3f);
      ms.setFadeOut(0.5f);
      ms.setSize(35);
      ms.setSizeChangeSpeed(35);
      ms.setColor(new Color(255,255,255,80));
      aEP_CombatEffectPlugin.Mod.addEffect(ms);

    }
  }


}
