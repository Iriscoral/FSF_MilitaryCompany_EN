package data.scripts.weapons;

import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.BeamEffectPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import combat.util.aEP_Tool;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.Random;

public class aEP_RepairBeam implements BeamEffectPlugin
{
  private static final float REPAIR_AMOUNT = 3f;
  private static final float REPAIR_PERCENT_PER_SEC = 0.1f;
  private static final float FSF_BONUS = 2f;
  private static final float REPAIR_THRESHOLD = 0.5f;
  private static final Color repairing = new Color(250, 250, 200, 220);
  private int timer = 0;


  @Override
  public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {
    if (beam.didDamageThisFrame() && beam.getDamageTarget() instanceof ShipAPI) {
      timer = timer + 1;
      if (timer < 3) {
        return;
      }
      timer = 0;


      ShipAPI ship = (ShipAPI) beam.getDamageTarget();
      Random rand1 = new Random();
      Random rand2 = new Random();
      Random rand3 = new Random();


      int xSize = ship.getArmorGrid().getLeftOf() + ship.getArmorGrid().getRightOf();
      int ySize = ship.getArmorGrid().getAbove() + ship.getArmorGrid().getBelow();
      float cellMaxArmor = ship.getArmorGrid().getMaxArmorInCell();
      float hullLevel = ship.getHullLevel();
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
      //absolute position of this grid
      Vector2f minArmorLoc = ship.getArmorGrid().getLocation(minX, minY);


      //repair this grid
      if (ship.getArmorGrid().getArmorValue(minX, minY) >= cellMaxArmor * REPAIR_THRESHOLD)//&& ship.getArmorGrid().getArmorValue(0,0) == cellMaxArmor)
      {
        //if it is full, add text
        engine.addFloatingText(ship.getLocation(), "No_Need_toRepair", 15f, repairing, ship, 0.25f, 20f);

      }
      else {
        //if it is not full, repair it
        float repairAmout = REPAIR_AMOUNT;
        float toRepair = aEP_Tool.Util.limitToTop(ship.getArmorGrid().getArmorValue(minX, minY) + repairAmout, cellMaxArmor);
        ship.getArmorGrid().setArmorValue(minX, minY, toRepair);


        //add sparks
        float randomX = rand1.nextInt(200) - 100;
        float randomY = rand1.nextInt(200) - 100;
        engine.addSmoothParticle(minArmorLoc,//added loc
          new Vector2f(randomX, randomY), //random initial speed
          30f, //size
          1f, //brightness
          0.8f,//duration
          repairing);     //color
        randomX = rand2.nextInt(200) - 100;
        randomY = rand2.nextInt(200) - 100;

        engine.addSmoothParticle(minArmorLoc,//added loc
          new Vector2f(randomX, randomY), //random initial speed
          45f, //size
          1f, //brightness
          0.8f,//duration
          repairing);     //color
        randomX = rand3.nextInt(200) - 100;
        randomY = rand3.nextInt(200) - 100;

        engine.addSmoothParticle(minArmorLoc,//added loc
          new Vector2f(randomX, randomY), //random initial speed
          30f, //size
          1f, //brightness
          0.5f,//duration
          repairing);     //color

      }

    }
  }

}

