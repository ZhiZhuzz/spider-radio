package radio.spider.app;
public class SleepTimerTest {
 public static void main(String[] args){
  for(int i=0;i<=720;i++)if(SleepTimer.parse(String.valueOf(i))!=i)throw new AssertionError();
  for(String v:new String[]{null,""," ","1.5","0.5","-1","721","1000","01","1e2","NaN","Infinity"," 15"}){
   try{SleepTimer.parse(v);throw new AssertionError("Accepted invalid timer: "+v);}catch(IllegalArgumentException expected){}
  }
  System.out.println("PASS 721 integer/cancel values and 13 invalid formats");
 }
}
