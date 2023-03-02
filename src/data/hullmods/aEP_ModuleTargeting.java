package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.impl.hullmods.BallisticRangefinder;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import combat.util.aEP_DataTool;
import combat.util.aEP_Tool;
import  com.fs.starfarer.api.combat.ShipAPI.HullSize;

import java.awt.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.fs.starfarer.api.impl.hullmods.BallisticRangefinder.*;
import static combat.util.aEP_DataTool.txt;

public class aEP_ModuleTargeting extends aEP_BaseHullMod
{
  private static String id = "aEP_ModuleTargeting";
  private static Map mag1 = new HashMap();
  static {
    mag1.put(HullSize.FIGHTER, 0f);
    mag1.put(HullSize.FRIGATE, 10f);
    mag1.put(HullSize.DESTROYER, 20f);
    mag1.put(HullSize.CRUISER, 40f);
    mag1.put(HullSize.CAPITAL_SHIP, 60f);
  }
  private static Map mag2 = new HashMap();
  static {
    mag2.put(HullSize.FIGHTER, 0f);
    mag2.put(HullSize.FRIGATE, 0f);
    mag2.put(HullSize.DESTROYER, 0f);
    mag2.put(HullSize.CRUISER, 35f);
    mag2.put(HullSize.CAPITAL_SHIP, 50f);
  }


  public aEP_ModuleTargeting() {
  }

  /**
   * 使用这个
   * @param ship
   * @param id
   */
  @Override
  public void applyEffectsAfterShipCreationImpl(ShipAPI ship, String id) {
    for(String mId : ship.getVariant().getModuleSlots()){
      ShipVariantAPI m = ship.getVariant().getModuleVariant(mId);
      String syncId = "";
      //禁止模块拥有以下插件
      syncId ="safetyoverrides";
      if(m.hasHullMod(syncId))
        m.removeMod(syncId);
      syncId = "ballistic_rangefinder";
      if(m.hasHullMod(syncId))
        m.removeMod(syncId);
      syncId = "targetingunit";
      if(m.hasHullMod(syncId))
        m.removeMod(syncId);
      syncId = "dedicated_targeting_core";
      if(m.hasHullMod(syncId))
        m.removeMod(syncId);
      syncId = "aEP_TargetSystem";
      if(m.hasHullMod(syncId))
        m.removeMod(syncId);
    }



  }

  /**
   * 在装配页面，module系统还没有初始化，只存在variant的关系，无法获得shipAPI
   * 在进入战场的第一帧加载buff
   * */
  @Override
  public void advanceInCombat(ShipAPI ship, float amount) {
    //只在舰船被部署的第一帧运行一次
    //舰船进入战斗时，才会提供加成
    if(ship.getFullTimeDeployed() > 0.00001f) return;
    for(ShipAPI m : ship.getChildModulesCopy()){
      //aEP_Tool.Util.addDebugLog("in-");
      String syncId = "";
      //防止模块用v排
      m.getMutableStats().getVentRateMult().modifyMult(id, 0);
      //禁止模块拥有机库
      m.getMutableStats().getNumFighterBays().modifyMult(id,0);
      //直接复制的原版测距仪，记得找最大槽位时用ship，加成时用m
      syncId = "ballistic_rangefinder";
      if(ship.getVariant().hasHullMod(syncId)) {
        //找本体上最大的槽位
        WeaponAPI.WeaponSize largest = null;
        for (WeaponSlotAPI slot : ship.getHullSpec().getAllWeaponSlotsCopy()) {
          if (slot.isDecorative()) continue;
          if (slot.getWeaponType() == WeaponAPI.WeaponType.BALLISTIC) {
            if (largest == null || largest.ordinal() < slot.getSlotSize().ordinal()) {
              largest = slot.getSlotSize();
            }
          }
        }
        //找本模块上最大的槽位
        for (WeaponSlotAPI slot : m.getHullSpec().getAllWeaponSlotsCopy()) {
          if (slot.isDecorative()) continue;
          if (slot.getWeaponType() == WeaponAPI.WeaponType.BALLISTIC) {
            if (largest == null || largest.ordinal() < slot.getSlotSize().ordinal()) {
              largest = slot.getSlotSize();
            }
          }
        }
        if (largest == null) return;
        float small = 0f;
        float medium = 0f;
        float max = 0f;
        switch (largest) {
          case LARGE:
            small = BONUS_SMALL_3;
            medium = BONUS_MEDIUM_3;
            max = BONUS_MAX_3;
            break;
          case MEDIUM:
            small = BONUS_SMALL_2;
            max = BONUS_MAX_2;
            break;
          case SMALL:
            small = BONUS_SMALL_1;
            max = BONUS_MAX_1;
            break;
        }
        m.addListener(new BallisticRangefinder.RangefinderRangeModifier(small, medium, max));
      }
      syncId = "targetingunit";
      if(ship.getVariant().hasHullMod(syncId)) {
        //直接复制的黄定位代码，记得舰船尺寸时用ship，加成时用m
        m.getMutableStats().getBallisticWeaponRangeBonus().modifyPercent(id, (Float) mag1.get(ship.getHullSize()));
        m.getMutableStats().getEnergyWeaponRangeBonus().modifyPercent(id, (Float) mag1.get(ship.getHullSize()));
      }
      syncId = "dedicated_targeting_core";
      if(ship.getVariant().hasHullMod(syncId)) {
        //直接复制的初始定位代码，记得舰船尺寸时用ship，加成时用m
        m.getMutableStats().getBallisticWeaponRangeBonus().modifyPercent(id, (Float) mag2.get(ship.getHullSize()));
        m.getMutableStats().getEnergyWeaponRangeBonus().modifyPercent(id, (Float) mag2.get(ship.getHullSize()));

      }
    }
  }

  @Override
  public String getDescriptionParam(int index, ShipAPI.HullSize hullSize, ShipAPI ship) {
    return "";
  }

  @Override
  public boolean shouldAddDescriptionToTooltip(ShipAPI.HullSize hullSize, ShipAPI ship, boolean isForModSpec) {
    return true;
  }

  @Override
  public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
    tooltip.addSectionHeading(txt("aEP_ModuleTargeting01"), Alignment.MID, 5f);
    tooltip.addPara("- "+ "{%s}", 5f, Color.white, Color.green, Global.getSettings().getHullModSpec("dedicated_targeting_core").getDisplayName());
    tooltip.addPara("- "+ "{%s}", 5f, Color.white, Color.green, Global.getSettings().getHullModSpec("targetingunit").getDisplayName());
    tooltip.addPara("- "+ "{%s}", 5f, Color.white, Color.green, Global.getSettings().getHullModSpec("ballistic_rangefinder").getDisplayName());
    tooltip.addSectionHeading(txt("aEP_ModuleTargeting03"), Alignment.MID, 5f);
    tooltip.addPara("- "+ "{%s}", 5f, Color.white, Color.red, Global.getSettings().getHullModSpec("safetyoverrides").getDisplayName());
    tooltip.addPara("- "+ "{%s}", 5f, Color.white, Color.red, Global.getSettings().getHullModSpec("aEP_TargetSystem").getDisplayName());
    tooltip.addPara("- "+ "{%s}", 5f, Color.white, Color.red, txt("aEP_ModuleTargeting04"));

    tooltip.addPara(aEP_DataTool.txt("aEP_ModuleTargeting02"), Color.gray, 5f);
  }
}
