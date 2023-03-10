package data.scripts.campaign.intel;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.procgen.SalvageEntityGenDataSpec;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.campaign.CampaignEntity;
import com.fs.starfarer.campaign.CustomCampaignEntity;
import com.fs.starfarer.rpg.OfficerData;
import combat.util.aEP_DataTool;
import combat.util.aEP_Tool;
import data.hullmods.aEP_CruiseMissileCarrier;
import data.scripts.campaign.entity.aEP_CruiseMissileEntityPlugin;
import data.scripts.campaign.items.aEP_CruiseMissile;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.LinkedHashSet;
import java.util.Set;

import static combat.util.aEP_DataTool.txt;
import static data.scripts.campaign.intel.aEP_CruiseMissileLoadIntel.S1_ITEM_ID;

public class aEP_AWM3Intel extends aEP_BaseMission
{
  CampaignFleetAPI targetFleet;
  SectorEntityToken token;
  String shipName;
  int didGenShip = 0;
  int didShipRecovered = 0;
  int didHighlightLili = 0;

  public aEP_AWM3Intel(SectorEntityToken whereToSpawn, String targetShipId) {
    this.sector = Global.getSector();
    this.faction = Global.getSector().getFaction("aEP_FSF");
    ending = false;
    ended = false;
    shipName = targetShipId;
    this.token = whereToSpawn;
    setName(this.getClass().getSimpleName());
    setPostingLocation(token);


    //add Fleet
    FleetParamsV3 para = new FleetParamsV3(new Vector2f(0f, 0f),
            "aEP_FSF",
            1f,// qualityMod
            FleetTypes.PERSON_BOUNTY_FLEET,
            80f, // combatPts
            0f, // freighterPts
            0f, // tankerPts
            0f, // transportPts
            0f, // linerPts
            0f,  // utilityPts
            1f);
    para.maxShipSize = 2;
    para.treatCombatFreighterSettingAsFraction = true;
    para.averageSMods = 1;
    para.ignoreMarketFleetSizeMult = true;
    CampaignFleetAPI targetFleet = FleetFactoryV3.createFleet(para);

    //??????????????????
    {
      FleetMemberAPI s = targetFleet.getFleetData().addFleetMember("aEP_NuanChi_Elite");
      PersonAPI p = faction.createRandomPerson();
      p.setFaction("pirates");
      p.setRankId(Ranks.SPACE_COMMANDER);
      p.setPersonality(Personalities.CAUTIOUS);
      //0-????????????1-?????????2-??????
      p.getStats().setSkillLevel(Skills.MISSILE_SPECIALIZATION, 2);
      p.getStats().setSkillLevel(Skills.COMBAT_ENDURANCE, 1);
      p.getStats().setSkillLevel(Skills.IMPACT_MITIGATION, 1);
      p.getStats().setSkillLevel(Skills.DAMAGE_CONTROL, 2);
      p.getStats().setSkillLevel(Skills.POINT_DEFENSE, 1);
      targetFleet.getFleetData().addOfficer(p);
      s.setCaptain(p);
      s.getVariant().addPermaMod("ecm", true);

      s = targetFleet.getFleetData().addFleetMember("aEP_NuanChi_Elite");
      p = faction.createRandomPerson();
      p.setFaction("pirates");
      p.setRankId(Ranks.SPACE_COMMANDER);
      p.setPersonality(Personalities.CAUTIOUS);
      p.getStats().setSkillLevel(Skills.MISSILE_SPECIALIZATION, 2);
      p.getStats().setSkillLevel(Skills.COMBAT_ENDURANCE, 1);
      p.getStats().setSkillLevel(Skills.IMPACT_MITIGATION, 1);
      p.getStats().setSkillLevel(Skills.DAMAGE_CONTROL, 2);
      p.getStats().setSkillLevel(Skills.POINT_DEFENSE, 1);
      targetFleet.getFleetData().addOfficer(p);
      s.setCaptain(p);
      s.getVariant().addPermaMod("ecm", true);

      s = targetFleet.getFleetData().addFleetMember("aEP_NuanChi_Support");
      p = faction.createRandomPerson();
      p.setFaction("pirates");
      p.setRankId(Ranks.SPACE_COMMANDER);
      p.setPersonality(Personalities.CAUTIOUS);
      p.getStats().setSkillLevel(Skills.MISSILE_SPECIALIZATION, 2);
      p.getStats().setSkillLevel(Skills.COMBAT_ENDURANCE, 1);
      p.getStats().setSkillLevel(Skills.IMPACT_MITIGATION, 1);
      p.getStats().setSkillLevel(Skills.DAMAGE_CONTROL, 2);
      p.getStats().setSkillLevel(Skills.POINT_DEFENSE, 1);
      targetFleet.getFleetData().addOfficer(p);
      s.setCaptain(p);
      s.getVariant().addPermaMod("ecm", true);

      s = targetFleet.getFleetData().addFleetMember("aEP_DuiLiu_Super");
      p = faction.createRandomPerson();
      p.setFaction("pirates");
      p.setRankId(Ranks.SPACE_COMMANDER);
      p.setPersonality(Personalities.STEADY);
      p.getStats().setSkillLevel(Skills.BALLISTIC_MASTERY, 2);
      p.getStats().setSkillLevel(Skills.HELMSMANSHIP, 2);
      p.getStats().setSkillLevel(Skills.IMPACT_MITIGATION, 1);
      p.getStats().setSkillLevel(Skills.DAMAGE_CONTROL, 2);
      p.getStats().setSkillLevel(Skills.CONTAINMENT_PROCEDURES, 1);
      p.getStats().setSkillLevel(Skills.FIELD_MODULATION, 2);
      p.getStats().setSkillLevel(Skills.TARGET_ANALYSIS, 2);
      targetFleet.getFleetData().addOfficer(p);
      s.setCaptain(p);
      s.getVariant().addPermaMod("ecm", true);

      s = targetFleet.getFleetData().addFleetMember("aEP_ZhongLiu_Broadside");
      p = faction.createRandomPerson();
      p.setFaction("pirates");
      p.setRankId(Ranks.SPACE_COMMANDER);
      p.setPersonality(Personalities.STEADY);
      p.getStats().setSkillLevel(Skills.BALLISTIC_MASTERY, 1);
      p.getStats().setSkillLevel(Skills.HELMSMANSHIP, 1);
      p.getStats().setSkillLevel(Skills.IMPACT_MITIGATION, 1);
      p.getStats().setSkillLevel(Skills.DAMAGE_CONTROL, 1);
      p.getStats().setSkillLevel(Skills.CONTAINMENT_PROCEDURES, 1);
      p.getStats().setSkillLevel(Skills.TARGET_ANALYSIS, 1);
      targetFleet.getFleetData().addOfficer(p);
      s.setCaptain(p);

      s = targetFleet.getFleetData().addFleetMember("aEP_ZhongLiu_Broadside");
      p = faction.createRandomPerson();
      p.setFaction("pirates");
      p.setRankId(Ranks.SPACE_COMMANDER);
      p.setPersonality(Personalities.STEADY);
      p.getStats().setSkillLevel(Skills.BALLISTIC_MASTERY, 1);
      p.getStats().setSkillLevel(Skills.HELMSMANSHIP, 1);
      p.getStats().setSkillLevel(Skills.IMPACT_MITIGATION, 1);
      p.getStats().setSkillLevel(Skills.DAMAGE_CONTROL, 1);
      p.getStats().setSkillLevel(Skills.CONTAINMENT_PROCEDURES, 1);
      p.getStats().setSkillLevel(Skills.TARGET_ANALYSIS, 1);
      targetFleet.getFleetData().addOfficer(p);
      s.setCaptain(p);

      s = targetFleet.getFleetData().addFleetMember("aEP_ZhongLiu_Standard");
      p = faction.createRandomPerson();
      p.setFaction("pirates");
      p.setRankId(Ranks.SPACE_COMMANDER);
      p.setPersonality(Personalities.STEADY);
      p.getStats().setSkillLevel(Skills.BALLISTIC_MASTERY, 1);
      p.getStats().setSkillLevel(Skills.HELMSMANSHIP, 1);
      p.getStats().setSkillLevel(Skills.IMPACT_MITIGATION, 1);
      p.getStats().setSkillLevel(Skills.DAMAGE_CONTROL, 1);
      p.getStats().setSkillLevel(Skills.CONTAINMENT_PROCEDURES, 1);
      p.getStats().setSkillLevel(Skills.TARGET_ANALYSIS, 1);
      targetFleet.getFleetData().addOfficer(p);
      s.setCaptain(p);

      s = targetFleet.getFleetData().addFleetMember("aEP_PuBu_Standard");
      p = faction.createRandomPerson();
      p.setFaction("pirates");
      p.setRankId(Ranks.SPACE_COMMANDER);
      p.setPersonality(Personalities.STEADY);
      p.getStats().setSkillLevel(Skills.HELMSMANSHIP, 1);
      p.getStats().setSkillLevel(Skills.IMPACT_MITIGATION, 1);
      p.getStats().setSkillLevel(Skills.DAMAGE_CONTROL, 1);
      targetFleet.getFleetData().addOfficer(p);
      s.setCaptain(p);

      s = targetFleet.getFleetData().addFleetMember("aEP_ShanHu_Standard");
      p = faction.createRandomPerson();
      p.setFaction("pirates");
      p.setRankId(Ranks.SPACE_COMMANDER);
      p.setPersonality(Personalities.STEADY);
      p.getStats().setSkillLevel(Skills.MISSILE_SPECIALIZATION, 1);
      p.getStats().setSkillLevel(Skills.HELMSMANSHIP, 1);
      p.getStats().setSkillLevel(Skills.IMPACT_MITIGATION, 1);
      p.getStats().setSkillLevel(Skills.DAMAGE_CONTROL, 1);
      targetFleet.getFleetData().addOfficer(p);
      s.setCaptain(p);

      s = targetFleet.getFleetData().addFleetMember("aEP_ShanHu_Attack");
      p = faction.createRandomPerson();
      p.setFaction("pirates");
      p.setRankId(Ranks.SPACE_COMMANDER);
      p.setPersonality(Personalities.STEADY);
      p.getStats().setSkillLevel(Skills.MISSILE_SPECIALIZATION, 1);
      p.getStats().setSkillLevel(Skills.HELMSMANSHIP, 1);
      p.getStats().setSkillLevel(Skills.IMPACT_MITIGATION, 1);
      p.getStats().setSkillLevel(Skills.DAMAGE_CONTROL, 1);
      targetFleet.getFleetData().addOfficer(p);
      s.setCaptain(p);
    }

    //???????????????????????????
    FleetMemberAPI flagship = targetFleet.getFleetData().addFleetMember("aEP_ShenCeng_mk2_Standard");
    flagship.getVariant().addPermaMod("reinforcedhull", true);
    flagship.setId(shipName);
    targetFleet.getFleetData().setFlagship(flagship);
    aEP_CruiseMissileCarrier.LoadingMissile loading = new aEP_CruiseMissileCarrier.LoadingMissile();
    loading.setFleetMember(flagship.getId());
    loading.setLoadedNum(1);
    Global.getSector().addScript(loading);


    targetFleet.getFlagship().setShipName("AWM Testing Obj CMC02");
    targetFleet.setFaction("pirates");
    targetFleet.setName(aEP_DataTool.txt("AWM03_mission03"));
    targetFleet.getFleetData().sort();
    //add captain
    PersonAPI person = Global.getFactory().createPerson();
    person.setPortraitSprite("graphics/portraits/portrait_pirate02.png");
    person.setName(new FullName("phrex","jin", FullName.Gender.MALE));
    person.setGender(person.getName().getGender());
    person.setFaction("pirates");
    person.setRankId(Ranks.SPACE_CAPTAIN);
    person.setVoice(Voices.VILLAIN);
    person.setPersonality(Personalities.STEADY);
    person.getStats().setSkillLevel(Skills.CONTAINMENT_PROCEDURES,1);
    person.getStats().setSkillLevel(Skills.COMBAT_ENDURANCE,1);
    person.getStats().setSkillLevel(Skills.COORDINATED_MANEUVERS,1);
    person.getStats().setSkillLevel(Skills.ELECTRONIC_WARFARE,1);
    person.getStats().setSkillLevel(Skills.SUPPORT_DOCTRINE,1);
    person.getStats().setSkillLevel(Skills.TACTICAL_DRILLS,1);
    person.getStats().setSkillLevel(Skills.CREW_TRAINING,1);
    person.getStats().setSkillLevel(Skills.FIGHTER_UPLINK,1);
    targetFleet.getFleetData().addOfficer(person);
    targetFleet.getFlagship().setCaptain(person);
    targetFleet.setCommander(person);
    targetFleet.forceSync();

    targetFleet.getCargo().addSpecial(new SpecialItemData(aEP_CruiseMissileCarrier.SPECIAL_ITEM_ID,null),30f);
    SalvageEntityGenDataSpec.DropData drop = new SalvageEntityGenDataSpec.DropData();
    drop.addSpecialItem(aEP_CruiseMissileCarrier.SPECIAL_ITEM_ID, 5);
    targetFleet.addDropValue(drop);


    token.getContainingLocation().spawnFleet(token, 0f, 0f, targetFleet);
    targetFleet.getAI().addAssignment(FleetAssignment.ORBIT_AGGRESSIVE, token, 999999999f, null);
    //???ai???????????????????????????????????????
    targetFleet.addScript(new EntityWantToMissileAttackPlayer(targetFleet));
    targetFleet.getMemoryWithoutUpdate().set("$isFSFPirate", true);
    targetFleet.getMemoryWithoutUpdate().set("$core_fightToTheLast", true);
    targetFleet.getMemoryWithoutUpdate().set("$cfai_holdVsStronger", true);
    targetFleet.getMemoryWithoutUpdate().set("$cfai_makeAllowDisengage", false);
    targetFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MISSION_IMPORTANT, true);
    targetFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_SHIP_RECOVERY, true);
    targetFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true);
    targetFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true);
    targetFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_REP_IMPACT, true);
    targetFleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_LOW_REP_IMPACT, true);
    this.targetFleet = targetFleet;
    setMapLocation(targetFleet);
    setPostingLocation(targetFleet);

    setImportant(true);
    Global.getSector().getIntelManager().addIntel(this);
    Global.getSector().getIntelManager().queueIntel(this);
  }

  @Override
  public void advanceImpl(float amount) {

    //??????????????????????????????
    boolean isGone = true;
    for (FleetMemberAPI member : targetFleet.getFleetData().getMembersListCopy()) {
      if (member.getId().equals(shipName)) {
        isGone = false;
      }
    }
    if (!targetFleet.isAlive()) isGone = true;

    //??????????????????????????????????????????????????????????????????????????????
    if (isGone && didGenShip == 0) {

      //???????????????????????????????????????????????????
      DerelictShipEntityPlugin.DerelictShipData params = new DerelictShipEntityPlugin.DerelictShipData(new ShipRecoverySpecial.PerShipData("aEP_ShenCeng_mk2_Standard", ShipRecoverySpecial.ShipCondition.WRECKED, 0f), false);
      params.ship.shipName = "Shenceng Prototype";
      params.ship.nameAlwaysKnown = true;
      params.durationDays = 999999999f;
      params.ship.addDmods = true;
      SectorEntityToken ship = BaseThemeGenerator.addSalvageEntity(targetFleet.getContainingLocation(),
              Entities.WRECK, Factions.NEUTRAL, params);
      //??????????????????
      Vector2f toLocation = targetFleet.getLocation();
      ship.setLocation(toLocation.x, toLocation.y);
      ship.setDiscoverable(true);

      //????????????????????????????????????
      ShipRecoverySpecial.ShipRecoverySpecialData params2 = new ShipRecoverySpecial.ShipRecoverySpecialData("Prototype");
      ShipRecoverySpecial.PerShipData data = new ShipRecoverySpecial.PerShipData("aEP_ShenCeng_mk2_Standard", ShipRecoverySpecial.ShipCondition.AVERAGE);
      data.addDmods = true;
      data.shipName = "Shenceng Prototype";
      data.condition = ShipRecoverySpecial.ShipCondition.AVERAGE;
      data.nameAlwaysKnown = true;
      params2.addShip(data);
      params2.storyPointRecovery = true;
      //?????????????????????????????????
      Misc.setSalvageSpecial(ship, params2);

      //?????????
      ship.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MISSION_IMPORTANT,true);
      ship.getMemoryWithoutUpdate().set(MemFlags.STORY_CRITICAL,true);
      //?????????????????????????????????
      setPostingLocation(ship);
      setMapLocation(ship);
      //????????????1
      didGenShip = 1;
    }

    //???????????????????????????????????????????????????
    if(didGenShip == 1 && didShipRecovered == 0){
      if(!mapLocation.isAlive()){
        didShipRecovered = 1;
        shouldEnd = 1;
      }
    }

    //???????????????????????????????????????????????????????????????????????????
    //??????FSF??????
    if(shouldEnd > 0 && didHighlightLili == 0){
      SectorEntityToken fsfBase = Global.getSector().getEconomy().getMarket("aEP_FSF_DefStation").getPrimaryEntity();
      setPostingLocation(fsfBase);
      setMapLocation(fsfBase);
      for(PersonAPI person : fsfBase.getMarket().getPeopleCopy()){
        if(person.getName().getFullName().toLowerCase().contains("lili yang")){
          person.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MISSION_IMPORTANT,true);
        }
      }
      didHighlightLili = 1;
    }

  }

  @Override
  public void readyToEnd() {
    targetFleet.despawn();
    SectorEntityToken fsfBase = Global.getSector().getEconomy().getMarket("aEP_FSF_DefStation").getPrimaryEntity();
    setPostingLocation(fsfBase);
    setMapLocation(fsfBase);
    for(PersonAPI person : fsfBase.getMarket().getPeopleCopy()){
      if(person.getName().getFullName().toLowerCase().contains("lili yang")){
        person.getMemoryWithoutUpdate().unset(MemFlags.MEMORY_KEY_MISSION_IMPORTANT);
      }
    }
  }

  @Override
  public String getIcon() {
    return Global.getSettings().getSpriteName("aEP_icons", "AWM1");
  }

  //this part control brief bar on lower left
  @Override
  public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
    Color h = Misc.getHighlightColor();
    Color g = Misc.getGrayColor();
    Color c = faction.getBaseUIColor();

    info.setParaFontDefault();
    info.addPara(txt("AWM03_title"), c, 3f);
    info.setBulletedListMode(BULLET);
    info.addPara(txt("AWM03_mission01"), 10f, g, h, token.getContainingLocation().getName(), ((PlanetAPI) token).getTypeNameWithWorld());

  }

  //this control big info part on right
  @Override
  public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
    Color hightLight = Misc.getHighlightColor();
    Color grayColor = Misc.getGrayColor();
    Color whiteColor = Misc.getTextColor();
    Color barColor = faction.getDarkUIColor();
    Color titleTextColor = faction.getColor();

    //??????????????????
    if(didGenShip==0){
      info.setParaFontDefault();
      info.addImages(250f, 90f, 3f, 10f, targetFleet.getCommander().getPortraitSprite(), targetFleet.getFaction().getCrest());
      info.addPara(txt("AWM03_mission02") + ": ", 10f, whiteColor, targetFleet.getFaction().getBaseUIColor(), targetFleet.getCommander().getNameString());
      info.addShipList(8, 1 + targetFleet.getMembersWithFightersCopy().size() / 8, 40f, barColor, targetFleet.getMembersWithFightersCopy(), 10f);
      info.addSectionHeading(txt("mission_require"), titleTextColor, barColor, Alignment.MID, 30f);
      info.addPara(txt("mission_destroy"), hightLight, 3f);
    }
    //???????????????????????????????????????????????????
    if(didGenShip==1) {
      info.setParaFontDefault();
      info.addImages(250f, 90f, 3f, 10f, targetFleet.getCommander().getPortraitSprite(), targetFleet.getFaction().getCrest());
      info.addPara(txt("mission_destroyed"), hightLight, 3f);
      info.addSectionHeading(txt("mission_require"), titleTextColor, barColor, Alignment.MID, 30f);
      if(didShipRecovered == 1){
        info.addPara(txt("mission_complete"), hightLight, 3f);
      }else {
        info.addPara(txt("AWM03_mission04"), hightLight, 3f);
      }

    }

    if (Global.getSettings().isDevMode()) {
      info.addPara("devMode force finish", Color.yellow, 10f);
      info.addButton("Finish Mission", "Finish Mission", 120, 20, 20f);
    }

  }

  @Override
  public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
    if (buttonId.equals("Finish Mission")) {
      shouldEnd = 1;
    }
  }

  @Override
  public Set<String> getIntelTags(SectorMapAPI map) {
    Set<String> tags = new LinkedHashSet();
    tags.add("aEP_FSF");
    tags.add("Missions");
    return tags;
  }

  @Override
  public String getSortString() {
    return "FSF";
  }


  class EntityWantToMissileAttackPlayer implements EveryFrameScript
  {
    CampaignFleetAPI token;
    //by day
    float launchInterval = 0f;
    float prepareTime = 0f;

    EntityWantToMissileAttackPlayer(CampaignFleetAPI fleet) {
      token = fleet;
    }

    /**
     * @return true when the script is finished and can be cleaned up by the engine.
     */
    @Override
    public boolean isDone() {
      for (FleetMemberAPI member : token.getMembersWithFightersCopy()) {
        if (member.getVariant().getHullSpec().getHullId().contains("aEP_ShenCeng_mk2")) {
          return false;
        }
      }
      return true;
    }

    /**
     * @return whether advance() should be called while the campaign engine is paused.
     */
    @Override
    public boolean runWhilePaused() {
      return false;
    }

    /**
     * @param amount in seconds. Use SectorAPI.getClock() to figure out how many campaign days that is.
     */
    @Override
    public void advance(float amount) {
      CampaignFleetAPI fleet = token;
      float amountToDay = Misc.getDays(amount);
      launchInterval += amountToDay;
      if (Global.getSector().getPlayerFleet().isVisibleToSensorsOf(fleet)
              && MathUtils.getDistance(fleet.getLocation(), Global.getSector().getPlayerFleet().getLocation()) < 1200
              && launchInterval > 2f){
        prepareTime += amountToDay;
        if(prepareTime > 0.25f){
          launchToPlayer(fleet);
          launchInterval = 0f;
          prepareTime = 0f;
        }
      }
    }

    void launchToPlayer(CampaignFleetAPI fleet) {

      CustomCampaignEntityAPI missile = fleet.getContainingLocation().addCustomEntity(
              null,
              null,
              "aEP_CruiseMissile",
              fleet.getFaction().getId());

      missile.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true);
      missile.setFacing(VectorUtils.getAngle(fleet.getLocation(),Global.getSector().getPlayerFleet().getLocation()));
      missile.setContainingLocation(fleet.getContainingLocation());
      missile.setLocation(fleet.getLocation().x, fleet.getLocation().y);
      aEP_CruiseMissileEntityPlugin plugin = (aEP_CruiseMissileEntityPlugin)missile.getCustomPlugin();
      plugin.setVariantId("aEP_CruiseMissile");
      plugin.setTargetFleet(Global.getSector().getPlayerFleet());
    }

  }
}
