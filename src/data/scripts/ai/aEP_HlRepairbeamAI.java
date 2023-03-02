//by Tartiflette, for the guiding part
//by a111164
package data.scripts.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import combat.util.aEP_Tool;
import data.scripts.weapons.aEP_RepairDroneBeam;
import org.lazywizard.lazylib.combat.WeaponUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

public class aEP_HlRepairbeamAI implements AutofireAIPlugin
{
  static final float INTERVAL_TIME = 2f;


  private final CombatEngineAPI engine;
  private final WeaponAPI weapon;
  private ShipAPI ship;
  private ShipAPI parentShip;
  private ShipAPI target;
  private String id;
  private boolean shouldFire = false;
  private Vector2f targetPo = new Vector2f(0f, 0f);
  private STATUS status;

  private float lifeTimer;
  private float timer;

  public aEP_HlRepairbeamAI(WeaponAPI weapon) {
    this.weapon = weapon;
    this.engine = Global.getCombatEngine();
    this.status = new findASpot();
  }

  @Override
  public boolean shouldFire() {
    return shouldFire;
  }

  @Override
  public void forceOff() {
    status = new findASpot();
  }

  @Override
  public Vector2f getTarget() {
    return targetPo;
  }

  @Override
  public WeaponAPI getWeapon() {
    return weapon;
  }

  @Override
  public ShipAPI getTargetShip() {
    return target;
  }

  @Override
  public MissileAPI getTargetMissile() {
    return null;
  }

  @Override
  public void advance(float amount) {
    this.ship = weapon.getShip();
    this.parentShip = weapon.getShip().getDroneSource();
    shouldFire = false;
    if (parentShip == null || parentShip.isFighter() || parentShip.isDrone()) {
      return;
    }
    status.advance(amount);

    timer = timer + amount;
    lifeTimer = lifeTimer + amount;
    if (targetPo == null) {
      timer = timer - INTERVAL_TIME;
      status = new findASpot();
      return;
    }

    if (lifeTimer > INTERVAL_TIME) {
      int[] minArmorGrid = getLowestArmor();
      if (aEP_Tool.isWithinArc(parentShip, weapon) && (!needRepair().isEmpty() || (parentShip.getArmorGrid().getArmorValue(minArmorGrid[0], minArmorGrid[1]) / parentShip.getArmorGrid().getMaxArmorInCell()) < aEP_RepairDroneBeam.MIN_ARMOR_PERCENT)) {
        //Global.getCombatEngine().addFloatingText(parentShip.getLocation(),  "d", 20f ,new Color (0, 100, 200, 240),ship, 0.25f, 120f);
        shouldFire = true;
      }
    }


  }


  List<Vector2f> needRepair() {
    List<Vector2f> toPick = new ArrayList<>();
    for (WeaponAPI w : parentShip.getAllWeapons()) {
      if (w.isDisabled() && !w.isPermanentlyDisabled() && aEP_Tool.isNormalWeaponType(w, true) && w.getCurrAngle() < w.getMaxHealth()) {
        toPick.add(w.getLocation());
      }
    }
    for (ShipEngineControllerAPI.ShipEngineAPI e : parentShip.getEngineController().getShipEngines()) {
      if (e.isDisabled() && !e.isPermanentlyDisabled() && e.getHitpoints() < e.getMaxHitpoints()) {
        toPick.add(e.getLocation());
      }
    }

    return toPick;
  }

  int[] getLowestArmor() {

    int xSize = parentShip.getArmorGrid().getLeftOf() + parentShip.getArmorGrid().getRightOf();
    int ySize = parentShip.getArmorGrid().getAbove() + parentShip.getArmorGrid().getBelow();
    float cellMaxArmor = parentShip.getArmorGrid().getMaxArmorInCell();
    float minArmorLevel = 10f;
    int minX = 0;
    int minY = 0;
    //find the lowest armor grid
    for (int x = 0; x <= xSize - 1; x++) {
      for (int y = 0; y <= ySize - 1; y++) {
        float armorNow = parentShip.getArmorGrid().getArmorValue(x, y);
        float armorLevel = (armorNow / cellMaxArmor);

        // get minArmorLevel position
        if (armorLevel <= minArmorLevel) {
          minArmorLevel = armorLevel;
          minX = x;
          minY = y;

        }
      }
    }

    return new int[]{minX, minY};
  }


  private interface STATUS
  {
    void advance(float amount);
  }

  class findASpot implements STATUS
  {
    @Override
    public void advance(float amount) {
      targetPo = parentShip.getLocation();
      status = new aimToTargetPo();

    }
  }

  class aimToTargetPo implements STATUS
  {
    @Override
    public void advance(float amount) {
      //back to searching mod if no targetPo
      if (targetPo == null) {
        status = new findASpot();
        return;
      }


      WeaponUtils.aimTowardsPoint(weapon, targetPo, WeaponUtils.getTimeToAim(weapon, targetPo));


    }
  }

  class selfDestruct implements STATUS
  {
    @Override
    public void advance(float amount) {
      aEP_Tool.killMissile(ship, engine);
    }
  }


}
