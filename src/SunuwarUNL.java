
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class SunuwarUNL {

    private final double utilMin = 30;
    private final double utilMax = 100;

    private final double arrivalRate = LabSetup.ARRIVAL_RATE;
    private double minPower = 0;

    private final int numOfGroups = LabSetup.FLOPS.length;
    private final ArrayList<double[]> powerCoEff[] = new ArrayList[numOfGroups];

    private final double groupUtilLow[] = new double[numOfGroups];
    private final double groupUtilHigh[] = new double[numOfGroups];
    private final double groupUtilTemp[] = new double[numOfGroups];
    private final double groupUtilFinal[] = new double[numOfGroups];

    private final double throughputCoEff[] = new double[numOfGroups];
    private final ArrayList<double[]> serverUtil = new ArrayList<>();
    private final HashMap<Integer, Double[]> speeds = new HashMap<>();

    public SunuwarUNL() throws Exception {

        for (int i = 0; i < numOfGroups; i++) {
            powerCoEff[i] = new ArrayList<>();
            for (int j = 0; j < LabSetup.SERVER_GROUP[i].length; j++) {
                powerCoEff[i].add(new double[]{LabSetup.ALPHAS[i][j], LabSetup.BETAS[i][j]});
            }
        }

        throughputCoEff[0] = LabSetup.FLOPS[0] / 10;
        throughputCoEff[1] = LabSetup.FLOPS[1] / 10;
        throughputCoEff[2] = LabSetup.FLOPS[2] / 10;

        initialize();

        findGroupUtilization(numOfGroups - 1);

        System.arraycopy(groupUtilFinal, 0, groupUtilTemp, 0, numOfGroups);

        for (int i = 0; i < numOfGroups; i++) {
            serverUtil.add(utilDistribute(i));
        }

        Double data[]; //speed, flop, alpha, beta
        for (int i = 0; i < numOfGroups; i++) {
            for (int j = 0; j < LabSetup.SERVER_GROUP[i].length; j++) {
                data = new Double[]{serverUtil.get(i)[j] / 100, LabSetup.FLOPS[i], LabSetup.ALPHAS[i][j], LabSetup.ALPHAS[i][j] + LabSetup.BETAS[i][j]};
                speeds.put(LabSetup.SERVER_GROUP[i][j], data);
            }
        }

//        for (int i = 1; i <= speeds.size(); i++) {
//            System.out.println(i + Arrays.toString(speeds.get(i)));
//        }
    }

    public HashMap<Integer, Double[]> getSpeeds() {
        return speeds;
    }

    private void initialize() throws Exception {
        for (int i = 0; i < numOfGroups; i++) {
            for (int j = 0; j < powerCoEff[i].size(); j++) {
                minPower += powerCoEff[i].get(j)[0] * utilMax / 100 + powerCoEff[i].get(j)[1];
            }
        }

        for (int i = 0; i < numOfGroups; i++) {
            groupUtilLow[i] = powerCoEff[i].size() * utilMin;
            groupUtilHigh[i] = powerCoEff[i].size() * utilMax;
            groupUtilTemp[i] = 0;
        }

    }

    private void findGroupUtilization(int i) throws Exception {

        if (i > 0) {

            double left = powerCoEff[i].size() * utilMin;

            double right = arrivalRate;
            for (int j = 0; j < i; j++) {
                right -= throughputCoEff[j] * groupUtilHigh[j];
            }
            for (int j = i + 1; j < numOfGroups; j++) {
                right -= throughputCoEff[j] * groupUtilTemp[j];
            }
            right /= throughputCoEff[i];

            groupUtilLow[i] = (left > right) ? left : right;

            left = powerCoEff[i].size() * utilMax;

            right = arrivalRate;
            for (int j = 0; j < i; j++) {
                right -= throughputCoEff[j] * groupUtilLow[j];
            }
            for (int j = i + 1; j < numOfGroups; j++) {
                right -= throughputCoEff[j] * groupUtilTemp[j];
            }
            right /= throughputCoEff[i];

            groupUtilHigh[i] = (left < right) ? left : right;

            groupUtilTemp[i] = Math.round(groupUtilLow[i]);
            findGroupUtilization(i - 1);

            groupUtilTemp[i] = Math.round(groupUtilHigh[i]);
            findGroupUtilization(i - 1);

        } else {

            groupUtilTemp[i] = arrivalRate;
            for (int j = 1; j < numOfGroups; j++) {
                groupUtilTemp[i] -= throughputCoEff[j] * groupUtilTemp[j];
            }
            groupUtilTemp[i] = Math.round(groupUtilTemp[i] / throughputCoEff[i]);

            if (groupUtilTemp[i] < groupUtilLow[i] || groupUtilTemp[i] > groupUtilHigh[i]) {
                throw new Exception("not a feasible solution");
            } else {

                double power = 0;
                for (int j = 0; j < numOfGroups; j++) {
                    double[] util = utilDistribute(j);
                    for (int k = 0; k < powerCoEff[j].size(); k++) {
                        power += powerCoEff[j].get(k)[0] * util[k] / 100 + powerCoEff[j].get(k)[1];
                    }
                }

                if (power <= minPower) {
                    minPower = power;
                    System.arraycopy(groupUtilTemp, 0, groupUtilFinal, 0, numOfGroups);
                }
            }
        }
    }

    private double[] utilDistribute(int groupIndex) throws Exception {

        double utilTemp = groupUtilTemp[groupIndex];

        double util[] = new double[powerCoEff[groupIndex].size()];

        double totalMin = powerCoEff[groupIndex].size() * utilMin;
        double totalMax = powerCoEff[groupIndex].size() * utilMax;

        if (utilTemp > totalMax) {
            throw new Exception("Error value for util");
        } else if (utilTemp < totalMin) {
            Arrays.fill(util, utilMin);
        } else {

            double m = Math.floor((utilTemp - totalMin) / (utilMax - utilMin));
            for (int i = 1; i <= powerCoEff[groupIndex].size(); i++) {
                if (i <= m) {
                    util[i - 1] = utilMax;
                } else if (i > m + 1) {
                    util[i - 1] = utilMin;
                } else {
                    util[i - 1] = utilTemp - m * utilMax - (powerCoEff[groupIndex].size() - m - 1) * utilMin;
                }
            }
        }

        return util;
    }

//    public static void main(String[] args) {
//
//        try {
//            new SunuwarUNL();
//        } catch (Exception ex) {
//            ex.printStackTrace();
//        }
//
//    }
}
