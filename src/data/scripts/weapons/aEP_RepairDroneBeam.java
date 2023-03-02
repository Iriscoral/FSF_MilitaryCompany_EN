package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import combat.util.aEP_Tool;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

import static combat.impl.VEs.AEP_RepairSparkKt.spawnRepairingSpark;

public class aEP_RepairDroneBeam implements BeamEffectPlugin
{
  public static final float MIN_ARMOR_PERCENT = 0.15f;
  private static final float REPAIR_AMOUNT = 20f;
  private static final float ARMOR_REPAIR_AMOUNT = 1f;
  private static final Color repairing = new Color(250, 250, 50, 120);
  private int timer = 0;


  @Override
  public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {
    if (beam.didDamageThisFrame() && beam.getDamageTarget() instanceof ShipAPI) {
      ShipAPI target = (ShipAPI) beam.getDamageTarget();
      if (target.isFighter() || target.isDrone()) {
        return;
      }

      timer = timer + 1;
      if (timer < 3) {
        return;
      }
      timer = 0;

      ShipAPI ship = (ShipAPI) beam.getDamageTarget();


      float repairAmount = REPAIR_AMOUNT;
      for (WeaponAPI w : ship.getAllWeapons()) {
        if (w.isDisabled() && !w.isPermanentlyDisabled() && repairAmount > 0 && aEP_Tool.isNormalWeaponType(w, true) && w.getCurrAngle() < w.getMaxHealth()) {
          float toRepair = Math.min(50f, w.getMaxHealth() - 1f - w.getCurrHealth());
          if (toRepair < 1) {
            continue;
          }
          toRepair = Math.min(toRepair, repairAmount);
          repairAmount = repairAmount - toRepair;
          w.setCurrHealth(w.getCurrHealth() + toRepair);
          spawnRepairingSpark(w.getLocation(),new Color(240,210,50,100));
        }
      }

      for (ShipEngineControllerAPI.ShipEngineAPI e : ship.getEngineController().getShipEngines()) {
        if (e.isDisabled() && !e.isPermanentlyDisabled() && repairAmount > 0) {
          float toRepair = Math.min(50f, e.getMaxHitpoints() - 1f - e.getHitpoints());
          if (toRepair < 1) {
            continue;
          }

          toRepair = Math.min(toRepair, repairAmount);
          repairAmount = repairAmount - toRepair;
          e.setHitpoints(e.getHitpoints() + toRepair);
          spawnRepairingSpark(e.getLocation(), new Color(240,210,50,100));

        }
      }


      int xSize = ship.getArmorGrid().getLeftOf() + ship.getArmorGrid().getRightOf();
      int ySize = ship.getArmorGrid().getAbove() + ship.getArmorGrid().getBelow();
      float cellMaxArmor = ship.getArmorGrid().getMaxArmorInCell();
      float minArmorLevel = 10f;
      int minX = 0;
      int minY = 0;
      //find the lowest armor grid
      for (int x = 0; x <= xSize - 1; x++) {
        for (int y = 0; y <= ySize - 1; y++) {
          float armorNow = ship.getArmorGrid().getArmorValue(x, y);
          float armorLevel = (armorNow / cellMaxArmor);

          // get minArmorLevel position
          if (armorLevel <= minArmorLevel) {
            minArmorLevel = armorLevel;
            minX = x;
            minY = y;

          }
        }
      }
      float lowestArmor = ship.getArmorGrid().getArmorValue(minX, minY);
      if (lowestArmor / cellMaxArmor < MIN_ARMOR_PERCENT) {
        float toRepair = Math.min(cellMaxArmor * MIN_ARMOR_PERCENT, lowestArmor + ARMOR_REPAIR_AMOUNT);
        ship.getArmorGrid().setArmorValue(minX, minY, toRepair);
        Vector2f loc = ship.getArmorGrid().getLocation(minX, minY);
        spawnRepairingSpark(loc,new Color(240,210,50,100));

      }


    }


  }

}

