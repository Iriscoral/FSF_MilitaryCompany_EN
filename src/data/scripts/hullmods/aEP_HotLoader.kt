package data.scripts.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import com.fs.starfarer.api.combat.WeaponAPI
import com.fs.starfarer.api.combat.listeners.AdvanceableListener
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import combat.util.aEP_DataTool
import combat.util.aEP_DataTool.txt
import combat.util.aEP_ID
import combat.util.aEP_Tool
import java.awt.Color

class aEP_HotLoader : aEP_BaseHullMod() {

  companion object {
    //private final static float RELOAD_THRESHOLD = 2;//by seconds
    const val RELOAD_PERCENT = 0.25f
    const val EXTRA_SPEED_ON_FIRE = 0.25f
    const val SMOD_BONUS = 0.5f

    const val EXTRA_SPEED_ON_SYSTEM = 0.5f
    const val ID = "aEP_HotLoader"

  }

  init {
    notCompatibleList.add("magazines")
    haveToBeWithMod.add("aEP_MarkerDissipation")
  }

  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {
    if (!ship.hasListenerOfClass(AmmoReloadFaster::class.java)) {
      ship.addListener(AmmoReloadFaster(ship))
    }

  }

  override fun applySmodEffectsAfterShipCreationImpl(ship: ShipAPI, stats:MutableShipStatsAPI, id: String) {
    stats.ballisticAmmoRegenMult.modifyPercent(id, SMOD_BONUS)
    stats.energyAmmoRegenMult.modifyPercent(id, SMOD_BONUS)
  }

  internal class AmmoReloadFaster(var ship: ShipAPI) : AdvanceableListener {
    val checkTracker = IntervalUtil(0.25f,0.25f)
    var heatLevel = 0f
    var reloadingMap: MutableMap<WeaponAPI, Float> = HashMap()

    override fun advance(amount: Float) {
      checkTracker.advance(amount)
      //每0.25秒计算一次全武器
      if(!checkTracker.intervalElapsed()) return
      val timePassed = checkTracker.elapsed
      heatLevel = aEP_MarkerDissipation.getBufferLevel(ship)
      var extra = EXTRA_SPEED_ON_FIRE * heatLevel
      for (w in ship.allWeapons) {
        //排除不用子弹的，系统武器，内置武器，和系统槽位上面的普通武器
        if(!aEP_Tool.isNormalWeaponSlotType(w.slot, false)) continue
        if(!w.usesAmmo() || w.ammo == Int.MAX_VALUE) continue

        //获取当前这个武器装了多少
        var weaponTimer = 0f
        reloadingMap[w]?: run { reloadingMap[w] = weaponTimer }
        weaponTimer = reloadingMap[w]!!

        //根据不同的武器类型计算弹药恢复速度
        var ammoPerSecond = w.spec.ammoPerSecond
        weaponTimer += ammoPerSecond * (RELOAD_PERCENT + extra) * timePassed
        //获取该武器一轮装填的量，cap到当前最大弹药数
        val reloadSize = w.ammoTracker.reloadSize.coerceAtMost(w.maxAmmo.toFloat())
        while (weaponTimer >= reloadSize) {
          weaponTimer -= reloadSize
          w.ammo = (w.ammo + reloadSize.toInt()).coerceAtMost(w.maxAmmo)
        }
        reloadingMap[w] = weaponTimer

      }
    }

  }




  override fun shouldAddDescriptionToTooltip(hullSize: HullSize, ship: ShipAPI?, isForModSpec: Boolean): Boolean {
    return true
  }

  override fun addPostDescriptionSection(tooltip: TooltipMakerAPI, hullSize: HullSize, ship: ShipAPI?, width: Float, isForModSpec: Boolean) {
    val faction = Global.getSector().getFaction(aEP_ID.FACTION_ID_FSF)
    val highLight = Misc.getHighlightColor()
    val grayColor = Misc.getGrayColor()
    val txtColor = Misc.getTextColor()
    val barBgColor = faction.getDarkUIColor()
    val factionColor: Color = faction.getBaseUIColor()
    val titleTextColor: Color = faction.getColor()

    tooltip.addSectionHeading(aEP_DataTool.txt("effect"),Alignment.MID, 5f)
    tooltip.addPara("{%s}"+ txt("aEP_HotLoader01"), 5f, arrayOf(Color.green), aEP_ID.HULLMOD_POINT, String.format("%.0f", RELOAD_PERCENT * 100f)+"%")

    tooltip.addSectionHeading(aEP_DataTool.txt("when_soft_up"),txtColor,barBgColor,Alignment.MID, 5f)
    tooltip.addPara("{%s}"+ txt("aEP_HotLoader02") , 5f, arrayOf(Color.green), aEP_ID.HULLMOD_POINT, String.format("%.0f", EXTRA_SPEED_ON_FIRE * 100f)+"%")

  }

  override fun getDescriptionParam(index: Int, hullSize: HullSize): String {
    if (index == 0) return "" + (RELOAD_PERCENT * 100).toInt() + "%"
    if (index == 0) return "" + (EXTRA_SPEED_ON_FIRE * 100).toInt() + "%"
    return ""
  }

  override fun hasSModEffect(): Boolean {
    return true
  }

  override fun addSModEffectSection(tooltip: TooltipMakerAPI, hullSize: HullSize?, ship: ShipAPI?, width: Float, isForModSpec: Boolean, isForBuildInList: Boolean) {
    val faction = Global.getSector().getFaction(aEP_ID.FACTION_ID_FSF)
    val highLight = Misc.getHighlightColor()
    val grayColor = Misc.getGrayColor()
    val txtColor = Misc.getTextColor()
    val barBgColor = faction.getDarkUIColor()
    val factionColor: Color = faction.getBaseUIColor()
    val titleTextColor: Color = faction.getColor()

    //Smod自带一个绿色的标题，不需要再来个标题
    //tooltip.addSectionHeading(aEP_DataTool.txt("effect"),Alignment.MID, 5f)

    tooltip.addPara("{%s}"+ txt("aEP_HotLoader03"), 5f, arrayOf(Color.green), aEP_ID.HULLMOD_POINT, String.format("%.0f", SMOD_BONUS * 100f)+"%")

    //tooltip.addSectionHeading(aEP_DataTool.txt("when_soft_up"),txtColor,barBgColor,Alignment.MID, 5f)
    //tooltip.addPara("{%s}"+ txt("aEP_HotLoader02") , 5f, arrayOf(Color.green), aEP_ID.HULLMOD_POINT, String.format("%.0f", EXTRA_SPEED_ON_FIRE * 100f)+"%")

  }
}