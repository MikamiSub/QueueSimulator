import java.io.*;
import java.math.BigDecimal;
import java.util.*;

import static java.lang.Math.log;
import static java.lang.Math.pow;

public class Main {

    /*入力変数*/
    static double ar = 0.7d;//平均到着率
    static double sr = 1.0d; //平均サービス率
    final static int simulationTimes = 10;//シミュレーション回数

    /*出力変数計算用*/
    static long sumWaitPacketsNum = 0L;//平均待ち行列数の総和
    static double sumTurnAroundTime = 0.0;//平均滞在時間の総和

    /*処理用変数*/
    static double nowTime = 0.0;//
    static int handlingPacketsNum = 0;//サーバーで処理されているパケット数

    /*統計用変数*/
    static long sumArrivePacketsNum = 0L; //パケットの総到着回数
    static long sumLossPacketsNum = 0L; //パケットの総破棄回数

    //待ち中のパケットを格納
    static private ArrayDeque<Packet> packetsQueue = new ArrayDeque<Packet>();

    //処理中のパケットを格納
    static private ArrayList<Packet> serverQue = new ArrayList<Packet>();

    //各種イベントを管理するDouble-EventFlagのTreeMap
    static private TreeMap<Double, LinkedList<Event>> eventManager = new TreeMap<Double,LinkedList<Event>>();

    //計算機,singleton
    private static Calculator calculator = Calculator.getInstance();

    public static void main(String[] args) {

        /* 入力変数 */
        final int serverNum = 1;//サーバーの数
        final long sysCap = 100000000000L; //K,システム容量
        final double simulationEndPacketsNum = 10E5;

        final boolean isServiceM = true;// M/M か　M/Dか

        //シミュレーション全体の1割を過渡条件として考慮
        int ignorePacketsNum = (int)((long)simulationEndPacketsNum/ 10);
        //過渡条件フラグ
        boolean isIgnore = false;

        //結果を出力するかのフラグ
        boolean isResultShow = true;


        for(int i = 0; i < 101 ;i++){

            /* 初回以外arを0.01ずつ増やす */
            if(i != 0){
                arUp();
            }

            /* 出力のリスト */
            List<Double> waitPacketsNumList = new ArrayList<Double>();
            List<Double> turnAroundTimeList = new ArrayList<Double>();
            List<Double> packetsLossList = new ArrayList<Double>();

            /* シミュレーション */
            for(int j = 0; j < simulationTimes; j++){

                /* 初期化 */
                init();
                packetsQueue.clear();
                serverQue.clear();
                eventManager.clear();

                //時刻0にインスタンス取得イベントを追加
                eventManager.put(nowTime, getEventList(Event.ARRIVE));

                /* シミュレーション */
                while(sumArrivePacketsNum < simulationEndPacketsNum ) {

                    /* 現時刻の更新 */
                    double eventTime = eventManager.firstKey();
                    nowTime = eventTime;

                    /* 次のイベントの取り出し */
                    LinkedList<Event> eventList = eventManager.get(eventTime);
                    Event event = eventList.pop();//eventListから最初のイベントを取り出す

                    if (eventList.size() >= 1) {//同時刻に複数のイベントがあった場合
                        eventManager.put(nowTime, eventList);
                    } else {
                        eventManager.remove(eventTime);
                    }

                    switch (event) {
                        /* パケット到着 */
                        case ARRIVE:

                            //新しいパケットのインスタンス取得
                            Packet newPacket = new Packet(nowTime,isServiceM,sr);
                            sumArrivePacketsNum++;

                            //待ち行列数の取得
                            int waitPacketsNum = packetsQueue.size() + serverQue.size();
                            newPacket.waitPacketsNum = waitPacketsNum;

                            //パケットをキューに入れる
                            if (waitPacketsNum < sysCap) {
                                packetsQueue.add(newPacket);
                            } else {
                                if ( !isIgnore || sumArrivePacketsNum >= ignorePacketsNum){
                                    sumLossPacketsNum++;
                                }
                            }

                            //次のパケット到着イベントの追加
                            double arriveInterval = arriveInterval();
                            double nextTime = nowTime + arriveInterval;
                            addEvent(nextTime, Event.ARRIVE);

                            //サーバーがパケットの処理を可能な場合
                            if (handlingPacketsNum < serverNum) {
                                startService();
                            }

                            break;
                        /* パケット処理終了 */
                        case END:

                            handlingPacketsNum--;

                            //処理終了時刻が一番小さいパケットの取り出し
                            int indexMin = 0;
                            double minEndTime = 0d;
                            for (int k = 0; k < serverQue.size(); k++) {
                                if (minEndTime >= serverQue.get(k).serviceEndTime || k == 0) {
                                    minEndTime = serverQue.get(k).serviceEndTime;
                                    indexMin = k;
                                }
                            }

                            Packet servedPacket = serverQue.get(indexMin);
                            serverQue.remove(indexMin);

                            //終了パケットから統計量の取り出し
                            if ( !isIgnore || sumArrivePacketsNum >= ignorePacketsNum ){
                                sumWaitPacketsNum += servedPacket.waitPacketsNum;
                                sumTurnAroundTime += servedPacket.turnAroundTime;
                            }

                            //次のパケットのサービス開始
                            if (packetsQueue.size() >= 1) {
                                startService();
                            }

                            break;
                    }

                }

                /* 統計量の計算 */
                if(isIgnore){
                    waitPacketsNumList.add( ((double)sumWaitPacketsNum / (double) (sumArrivePacketsNum - sumLossPacketsNum -ignorePacketsNum)) );
                    turnAroundTimeList.add( sumTurnAroundTime / (double) (sumArrivePacketsNum - sumLossPacketsNum -ignorePacketsNum) );
                    packetsLossList.add( sumLossPacketsNum / (double)( sumArrivePacketsNum - ignorePacketsNum) );
                }else {
                    waitPacketsNumList.add( (double)sumWaitPacketsNum / (double) (sumArrivePacketsNum - sumLossPacketsNum) );
                    turnAroundTimeList.add( sumTurnAroundTime / (double) (sumArrivePacketsNum - sumLossPacketsNum) );
                    packetsLossList.add( sumLossPacketsNum / (double)(sumArrivePacketsNum) );
                }

            }

            /* 統計量の平均の計算 */
            double aveWaitPacketsNum = calculator.average(waitPacketsNumList);
            double aveTurnAroundTime = calculator.average(turnAroundTimeList);
            double avePacketsLossRate = calculator.average(packetsLossList);

            //平均の平均2乗誤差
            double waitPacketsError = calculator.error(aveWaitPacketsNum,waitPacketsNumList);
            double turnAroundError = calculator.error(aveTurnAroundTime,turnAroundTimeList);
            double packetsLossError = calculator.error(avePacketsLossRate,packetsLossList);

            /* 理論値の計算 */
            double p = ar / (sr * (double)serverNum) ;
            // Theoretical
            // theoreticalAveLoss
            double theoreticalAveNum = -1d;
            double theoreticalAveTime = -1d;
            double aveSysLoss = -1d;

            if(sysCap <= 10E4) {
                if( serverNum == 1){
                    double powK = pow(p,sysCap);
                    double powK_plus = powK * p;

                    theoreticalAveNum = p * (1d - (double)(sysCap+1) * powK + (double)sysCap * powK_plus ) / ( (1d-p) * (1d-powK_plus ) );
                    theoreticalAveTime = theoreticalAveNum / ar;
                    aveSysLoss = powK * (1d - p) / (1d - powK_plus);
                }
            }else {
                if ( serverNum == 2){
                    double calBuf = (2.0d * p * p * p ) / (1 - p * p) ;
                    theoreticalAveNum = (calBuf + (double)serverNum * p);
                    theoreticalAveTime = (calBuf + (double)serverNum * p) / ar;
                } else if ( serverNum == 1 ) {
                    theoreticalAveNum = ( p / (1.0d - p) );
                    theoreticalAveTime = (p / (1.0d - p) / ar);
                }
            }

            /* 統計量の出力 */
            if( isResultShow ){
                String s = Long.toString(sysCap);
                if(sysCap >= 1.0E05){
                    s = "∞";
                }
                String ss;
                if(isServiceM){
                    ss = "M";
                }else{
                    ss = "D";
                }
                System.out.println("------M/" +ss + "/"+ serverNum + "/" + s + "------");
                System.out.println("平均到着率:" + ar);
                if ( theoreticalAveNum != -1d){
                    System.out.println("平均システム内パケット数理論値:" +theoreticalAveNum);
                    System.out.println("平均システム内パケット滞在時間理論値:" + theoreticalAveTime );
                    //System.out.println("パケット廃棄率理論値:" + aveSysLoss );
                }
                System.out.println("平均システム内パケット数:" + aveWaitPacketsNum);
                System.out.println("平均システム滞在時間　　:" + aveTurnAroundTime);
                System.out.println("パケット廃棄率          :" + avePacketsLossRate);

            }

            /* csvに書き込み */
            try {
                File csv = new File("writers.csv"); // CSVデータファイル
                // 追記モード
                BufferedWriter bw
                        = new BufferedWriter(new FileWriter(csv, true));
                /* 平均到着率,平均システム内パケット数,平均システム内パケット滞在時間,パケット廃棄率,平均パケット数理論値,平均システム内パケット滞在時間理論値,
                    パケット廃棄率理論値,パケット数誤差,パケット滞在時間誤差,パケット廃棄率誤差 */
                bw.write(ar + "," + aveWaitPacketsNum + "," + aveTurnAroundTime + "," + avePacketsLossRate+"," + theoreticalAveNum+ "," +theoreticalAveTime + ","+
                        aveSysLoss + "," + waitPacketsError  + "," + turnAroundError + "," + packetsLossError);
                bw.newLine();
                bw.close();

            } catch (FileNotFoundException e) {
                // Fileオブジェクト生成時の例外捕捉
                e.printStackTrace();
            } catch (IOException e) {
                // BufferedWriterオブジェクトのクローズ時の例外捕捉
                e.printStackTrace();
            }

        }

    }

    /* 各種変数の初期化 */
    private static void init() {
        handlingPacketsNum = 0;
        nowTime = 0.00;
        sumWaitPacketsNum = 0L;
        sumTurnAroundTime = 0.0;
        sumArrivePacketsNum = 0; //パケットの総到着回数
        sumLossPacketsNum = 0; //パケット破棄回数
    }

    /* 平均到着率を正確に0.01ずつ刻む */
    private static void arUp(){
        BigDecimal a = new BigDecimal("0.01");
        BigDecimal x = new BigDecimal(String.valueOf(ar));
        ar = x.add(a).doubleValue();
    }

    /* パケットの到着間隔を計算 */
    private static double arriveInterval(){
        return -log( 1.0 - Math.random()) / ar;
    }

    /*システムの先頭のパケットの処理開始*/
    private static void startService() {
        handlingPacketsNum++;

        Packet handlingPacket = packetsQueue.poll();

        //処理終了時刻の決定
        handlingPacket.calculateTime(nowTime);
        double handlingEndTime = handlingPacket.serviceEndTime;

        //処理開始パケットをサーバーのキューに追加
        serverQue.add(handlingPacket);

        //イベントマネージャーにENDイベントを追加
        addEvent(handlingEndTime, Event.END);

    }

    /* conflict時 */
    //桁落ちが発生するような値に突入してくるとコンフリクトが発生する
    private static void addEvent(double time, Event event) {
        if (eventManager.get(time) != null) {//イベント衝突時
            System.out.println("conflict");
            LinkedList<Event> bufList = eventManager.get(time);
            bufList.add(event);
            eventManager.put(time,bufList);
        }else {
            eventManager.put(time, getEventList(event));
        }
    }
    /* イベントリストを作る */
    private static LinkedList<Event> getEventList(Event event) {
        LinkedList<Event> list = new LinkedList<Event>();
        list.add(event);
        return list;
    }

}

