
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import org.apache.commons.io.IOUtils;

public class SimGridJava {

    private final int mapSizeMB = 128;
    private final int numOfMachines = 3000;
    private final String platformLab = "platform_lab.xml";
    private final String deploymentLab = "deployment_lab.xml";
    private final String simulatorPath = new File("").getAbsolutePath() + "/application_lab";

    private final HashMap<Integer, Double[]> speeds = new HashMap<>();
    private final double arrivalRate = LabSetup.ARRIVAL_RATE / 1024;
    private final String method;

    public SimGridJava(String method) throws Exception {
        this.method = method;

        if (this.method.equals("sunuwar")) {
            speeds.putAll(new SunuwarUNL().getSpeeds());
        } else {
            Double data[]; //speed, flop, alpha, beta
            for (int i = 0; i < LabSetup.FLOPS.length; i++) {
                for (int j = 0; j < LabSetup.SERVER_GROUP[i].length; j++) {
                    data = new Double[]{1.0, LabSetup.FLOPS[i], LabSetup1.ALPHAS[i][j], LabSetup1.ALPHAS[i][j] + LabSetup1.BETAS[i][j]};
                    speeds.put(LabSetup.SERVER_GROUP[i][j], data);
                }
            }
        }

        switch (this.method) {
            case "sunuwar":
                editPlatformFile(numOfMachines);
                for (int w = 0; w < LabSetup.WORKLOAD.length; w++) {
                    editDeploymentFile(LabSetup.WORKLOAD[w]);
                    String output = runSimulator();
                    System.out.print(arrivalRate + "," + (w + 1) + "," + output);
                }
                break;
            case "static":
                editPlatformFile(numOfMachines);
                for (int w = 0; w < LabSetup.WORKLOAD.length; w++) {
                    editDeploymentFile(LabSetup.WORKLOAD[w]);
                    String output = runSimulator();
                    System.out.print(arrivalRate + "," + (w + 1) + "," + output);
                }
                break;
            case "gmc_train":
                int workload[] = {/*5, 10, 50, 100, 500*/5000};
                for (int m = 2; m <= numOfMachines; m++) {
                    editPlatformFile(m);
                    for (int w = 0; w < workload.length; w++) {
                        editDeploymentFile(workload[w]);
                        String output = runSimulator();
                        System.out.print(arrivalRate + "," + (w + 1) + "," + output);
                    }
                }
                break;
            case "gmc_test":
                WekaML gmc = new WekaML(numOfMachines, "eptp");
                long after = System.currentTimeMillis(),
                 before;
                for (int i = 0; i < 3; i++) {
                    for (int w = 0; w < LabSetup.WORKLOAD.length; w++) {
                        ArrayList<Double> optimumConfig = gmc.runWekaML(LabSetup.WORKLOAD[w] * mapSizeMB);
                        System.out.println((w + 1) + "," + optimumConfig);

                        int m = (int) Math.round(optimumConfig.get(1));
                        editPlatformFile(m);
                        editDeploymentFile(LabSetup.WORKLOAD[w]);

                        before = System.currentTimeMillis() - after;
                        String output = runSimulator();
                        after = System.currentTimeMillis();

                        System.out.print(before + "," + (w + 1) + "," + output);

                        double thresTemp = optimumConfig.get(5);
                        double envTemp = optimumConfig.get(6);
                        gmc.updateHistory(output, thresTemp, envTemp);
                    }
                }
                break;
            default:
                System.out.println("no suitable method found");
                break;
        }

    }

    private void editPlatformFile(int numOfMachines) throws Exception {

        String dataXML = "<?xml version='1.0'?>\n"
                + "<!DOCTYPE platform SYSTEM \"http://simgrid.gforge.inria.fr/simgrid/simgrid.dtd\">\n"
                + "<platform version=\"4.1\">\n"
                + "\t<zone id=\"AIRL\" routing=\"Cluster\">\n"
                + "\t\t<link id=\"cable\" bandwidth=\"125MBps\" latency=\"0\"/>\n"
                + "\t\t<host id=\"slave0\" speed=\"500Mf\">"
                + "<prop id=\"watt_per_state\" value=\"60:80\"/>"
                + "<prop id=\"watt_off\" value=\"0\"/>"
                + "</host>\n"
                + "\t\t<host_link id=\"slave0\" up=\"cable\" down=\"cable\"/>\n";

        if (method.equals("sunuwar")) {
            for (int i = 1; i <= numOfMachines; i++) {
                Double[] data = speeds.get(i);
                dataXML += "\t\t<host id=\"slave" + i + "\" speed=\"" + Math.round(data[0] * data[1]) + "Mf\">"
                        + "<prop id=\"watt_per_state\" value=\"" + data[2] + ":" + data[3] + "\"/>"
                        + "<prop id=\"watt_off\" value=\"0\"/>"
                        + "</host>\n"
                        + "\t\t<host_link id=\"slave" + i + "\" up=\"cable\" down=\"cable\"/>\n";
            }
        } else {
            for (int i = 0; i < numOfMachines; i++) {
                int m = LabSetup.SERVERS[i];
                Double[] data = speeds.get(m);
                dataXML += "\t\t<host id=\"slave" + m + "\" speed=\"" + data[1] + "Mf\">"
                        + "<prop id=\"watt_per_state\" value=\"" + data[2] + ":" + data[3] + "\"/>"
                        + "<prop id=\"watt_off\" value=\"0\"/>"
                        + "</host>\n"
                        + "\t\t<host_link id=\"slave" + m + "\" up=\"cable\" down=\"cable\"/>\n";
            }
        }

        dataXML += "\t</zone>\n</platform>";

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(platformLab))) {
            bw.write(dataXML);
        }
    }

    private void editDeploymentFile(int task) throws Exception {

        String dataXML = "<?xml version='1.0'?>\n"
                + "<!DOCTYPE platform SYSTEM \"http://simgrid.gforge.inria.fr/simgrid/simgrid.dtd\">\n"
                + "<platform version=\"4.1\">\n"
                + "\t<actor host=\"slave0\" function=\"master\">\n"
                + "\t\t<argument value=\"" + task + "\"/>\n"
                + "\t\t<argument value=\"" + task * mapSizeMB + "e6\"/>\n"
                + "\t\t<argument value=\"125e6\"/>\n"
                + "\t</actor>\n"
                + "</platform>";

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(deploymentLab))) {
            bw.write(dataXML);
        }
    }

    private String runSimulator() throws Exception {
        Process pr = Runtime.getRuntime().exec(simulatorPath);
        return IOUtils.toString(pr.getInputStream(), "UTF-8");
    }

    public static void main(String[] args) {

        try {
            new SimGridJava("gmc_train");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
