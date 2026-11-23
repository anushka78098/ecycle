import com.ecycle.backend.engine.*;
import com.ecycle.shared.Enums.*;
import java.math.BigDecimal;
import java.util.*;
public class Check {
  static int fails=0;
  static void ok(boolean c,String m){ if(!c){fails++;System.out.println("FAIL: "+m);} else System.out.println("ok   : "+m);}
  public static void main(String[] a){
    var cv=new CircularValueEngine(null);
    var good=cv.evaluate(new CircularValueEngine.Input(FunctionalStatus.FULLY_WORKING,ItemCondition.LIKE_NEW,6,70,70,72,HazardLevel.LOW));
    System.out.println(good);
    ok(good.pathway()==Pathway.REUSE && good.overall()>75,"nearly-new working laptop -> REUSE");
    var mid=cv.evaluate(new CircularValueEngine.Input(FunctionalStatus.PARTIALLY_WORKING,ItemCondition.GOOD,30,70,70,72,HazardLevel.LOW));
    System.out.println(mid); ok(mid.pathway()==Pathway.REFURBISH||mid.pathway()==Pathway.REPAIR,"partially working laptop -> refurbish/repair");
    var dead=cv.evaluate(new CircularValueEngine.Input(FunctionalStatus.NOT_WORKING,ItemCondition.SCRAP,200,10,60,36,HazardLevel.LOW));
    System.out.println(dead); ok(dead.pathway()==Pathway.RECYCLE||dead.pathway()==Pathway.DISPOSE,"scrap battery -> recycle/dispose");
    var haz=cv.evaluate(new CircularValueEngine.Input(FunctionalStatus.FULLY_WORKING,ItemCondition.GOOD,12,55,45,48,HazardLevel.CRITICAL));
    ok(haz.pathway()==Pathway.RECYCLE,"critical hazard forces specialist recycle");
    ok(good.overall()<=100&&dead.overall()>=0,"scores bounded 0..100");
    var bs=new BidScoreEngine(null);
    double hi=bs.score(new BidScoreEngine.Input(1000,1000,true,80,80));
    double lo=bs.score(new BidScoreEngine.Input(600,1000,false,20,20));
    ok(hi>lo,"better bid scores higher ("+hi+" vs "+lo+")");
    ok(bs.score(new BidScoreEngine.Input(1000,1000,true,100,100))==100.0,"perfect bid = 100");
    double cheapGreen=bs.score(new BidScoreEngine.Input(700,1000,true,100,100)), richDirty=bs.score(new BidScoreEngine.Input(1000,1000,false,10,10));
    ok(cheapGreen>richDirty,"highest financial offer need not have highest Circular Bid Score ("+cheapGreen+" vs "+richDirty+")");
    var he=new HazardEngine();
    var rules=List.of(new HazardEngine.Rule("BATTERY_SWELLING","swollen",40,true,false,"isolate","Battery recycler"),
      new HazardEngine.Rule("STORAGE_DEVICE_PRESENT","storage",5,false,true,"sanitize","DSP"),
      new HazardEngine.Rule("PHYSICAL_DAMAGE","damage",10,false,false,"pad",null));
    var r=he.assess(rules,Map.of("BATTERY_SWELLING",true,"STORAGE_DEVICE_PRESENT",true));
    System.out.println(r); ok(r.blocked()&&r.requiresSanitization()&&r.level()==HazardLevel.HIGH,"swollen battery + storage -> blocked, sanitize, HIGH");
    var none=he.assess(rules,Map.of()); ok(none.level()==HazardLevel.LOW&&!none.blocked(),"no answers -> LOW, not blocked");
    var only=he.assess(rules,Map.of("PHYSICAL_DAMAGE",true)); ok(only.level()==HazardLevel.LOW&&only.totalPoints()==10,"damage only -> LOW (10 pts)");
    var ec=new EnvironmentalCalculator(); var f=new EnvironmentalCalculator.Factors(35,28,30,9,22);
    var e=ec.calculate(2.0,3,Pathway.RECYCLE,f,false); System.out.println(e);
    ok(e.ewasteDivertedKg()==6.0&&e.carbonAvoidedKg()==54.0&&Math.abs(e.materialsRecoveredKg()-1.32)<1e-9,"recycle 3x2kg: 6kg, 54 kgCO2e, 1.32kg metals");
    ok(ec.calculate(2,1,Pathway.DISPOSE,f,false).carbonAvoidedKg()==0,"dispose avoids nothing");
    try{ec.calculate(0,1,Pathway.REUSE,f,false);ok(false,"zero weight rejected");}catch(IllegalArgumentException x){ok(true,"zero weight rejected");}
    var me=new MatchingEngine();
    var req=new MatchingEngine.Req(2L,new BigDecimal("30000"),"Patiala",ItemCondition.GOOD,"office work student",  "ssd ram",Pathway.REFURBISH);
    var m=me.rank(req,List.of(
      new MatchingEngine.Cand(1L,2L,new BigDecimal("25000"),"Patiala",ItemCondition.GOOD,"Dell laptop for office work with 8GB RAM and SSD",Pathway.REFURBISH,80),
      new MatchingEngine.Cand(2L,2L,new BigDecimal("50000"),"Delhi",ItemCondition.POOR,"Old laptop",Pathway.RECYCLE,30),
      new MatchingEngine.Cand(3L,5L,new BigDecimal("1000"),"Patiala",ItemCondition.GOOD,"Monitor",Pathway.REUSE,90)));
    m.forEach(System.out::println);
    ok(!m.isEmpty()&&m.get(0).listingId()==1L,"best match ranked first with reasons");
    ok(m.stream().noneMatch(x->x.listingId()==3L),"other category excluded by hard filter");
    System.out.println(fails==0?"ALL ENGINE CHECKS PASSED":fails+" FAILURES"); System.exit(fails);
  }
}
