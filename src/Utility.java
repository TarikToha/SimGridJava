
import java.util.Random;

public class Utility {

    double alpha[] = {71.58, 70.3, 70.08, 70.47, 77.48, 75.39, 129.56, 84.09};
    double beta[] = {192.84, 194.05, 194.93, 200.6, 196.99, 198.79, 165.26, 249.17};
    Random rand = new Random();

    public Utility() {

        for (int i = 0; i < 400; i++) {
            double a = beta[rand.nextInt(beta.length)];//alpha[rand.nextInt(alpha.length)];
            double b = beta[rand.nextInt(beta.length)];
            System.out.println(a + "\t" + b);
        }
    }

//    public static void main(String[] args) {
//        new Utility();
//    }
}
