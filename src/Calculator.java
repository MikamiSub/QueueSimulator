import java.util.LinkedList;
import java.util.List;

class Calculator {

    private static Calculator ourInstance = new Calculator();

    public static Calculator getInstance() {
        return ourInstance;
    }

    private Calculator() {
    }

    double average (List<Double> list){
        double sum = 0.00;

        for (Double d : list){
            sum += d;
        }

        return sum / (double)list.size();
    }

    /* 平均値の平均2乗誤差 */
    double error (double ave, List<Double> list ){

        double r = 0.0;
        long n = list.size();

        for (Double d : list){
            r += (d - ave) * (d - ave) ;
        }

        return Math.sqrt( r / (double)n / (double)(n-1) );
    }


}
