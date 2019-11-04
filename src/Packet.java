import static java.lang.Math.log;

class Packet {

    private double arriveTime;//インスタンス取得時刻
    private double handlingTime;//処理に必要な時間

    double serviceEndTime;//イベント終了時刻
    double turnAroundTime;//ターンアラウンドタイム

    int waitPacketsNum;//到着時のシステムのパケット数

    Packet(double nowTime,boolean isServiceM, double sr){
        arriveTime = nowTime;
        handlingTime = handleTime(isServiceM, sr);
    }

    private double handleTime(boolean isServiceM, double sr){
        if(isServiceM){
            return -log( 1.0 - Math.random()) / sr;
        }else{
            return 1d / sr;
        }
    }

    void calculateTime(double nowTime){
        //イベント処理開始時刻
        serviceEndTime = nowTime + handlingTime;
        //待ち時間
        double waitTime = nowTime - arriveTime;
        turnAroundTime = waitTime + handlingTime;
    }


}
