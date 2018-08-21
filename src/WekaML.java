
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import weka.attributeSelection.ASEvaluation;
import weka.attributeSelection.ASSearch;
import weka.attributeSelection.AttributeSelection;
import weka.classifiers.AbstractClassifier;
import weka.classifiers.Classifier;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;

public class WekaML {

    private final int numOfMachines;
    private final String features[][] = {{"rt", "ResponseTime"}, {"cpu", "CPUPower"}, {"ac", "ThresTemp, EnvTemp, ACPower"}};
//    private final String header[] = {"WorkLoad", "NumOfMachines", "ResponseTime", "CPUPower", "CPUEnergy",
//        "ThresTemp", "EnvTemp", "ACPower", "ACEnergy", "TotalEnergy"};
    private final ArrayList<Double> testSet[];
    private final boolean total;

    public WekaML(int numOfMachines, boolean total) {
        this.numOfMachines = numOfMachines - 1;
        testSet = new ArrayList[this.numOfMachines];
        this.total = total;
    }

    public ArrayList<Double> runWekaML(double workload) throws Exception {

        for (int i = 0; i < testSet.length; i++) {
            testSet[i] = new ArrayList<>();
            testSet[i].add(workload);
            testSet[i].add((i + 2) * 1.0);
        }

        predictResponseTime(fetchData(features[0]));

        predictCPUPower(fetchData(features[1]));
        calculateCPUEnergy();

        Instances coolData = fetchData(features[2]);

        Instance recent = coolData.lastInstance();
        for (ArrayList<Double> test : testSet) {
            test.add(recent.value(2));
            test.add(recent.value(3));
        }

        predictACPower(coolData);
        calculateACEnergy();

        return calculateTotalEnergy();
    }

    private void predictResponseTime(Instances instances) throws Exception {

        Instances labeled = new Instances(instances);

        AttributeSelection as = new AttributeSelection();
        ASSearch asSearch = ASSearch.forName("weka.attributeSelection.BestFirst", new String[]{"-D", "1", "-N", "10"});
        as.setSearch(asSearch);
        ASEvaluation asEval = ASEvaluation.forName("weka.attributeSelection.CfsSubsetEval", new String[]{"-M", "-L"});
        as.setEvaluator(asEval);
        as.SelectAttributes(instances);
        instances = as.reduceDimensionality(instances);
        Classifier classifier = AbstractClassifier.forName("weka.classifiers.lazy.IBk", new String[]{"-E", "-K", "6", "-X", "-I"});
        classifier.buildClassifier(instances);

        predictLabel(labeled, classifier);
    }

    private void predictCPUPower(Instances instances) throws Exception {

        Instances labeled = new Instances(instances);

        AttributeSelection as = new AttributeSelection();
        ASSearch asSearch = ASSearch.forName("weka.attributeSelection.BestFirst", new String[]{"-D", "1", "-N", "4"});
        as.setSearch(asSearch);
        ASEvaluation asEval = ASEvaluation.forName("weka.attributeSelection.CfsSubsetEval", new String[]{});
        as.setEvaluator(asEval);
        as.SelectAttributes(instances);
        instances = as.reduceDimensionality(instances);
        Classifier classifier = AbstractClassifier.forName("weka.classifiers.meta.AdditiveRegression", new String[]{"-S", "1", "-I", "13", "-W", "weka.classifiers.lazy.IBk", "--", "-E", "-K", "53", "-X", "-I"});
        classifier.buildClassifier(instances);

        predictLabel(labeled, classifier);
    }

    private void predictACPower(Instances instances) throws Exception {

        Instances labeled = new Instances(instances);

        AttributeSelection as = new AttributeSelection();
        ASSearch asSearch = ASSearch.forName("weka.attributeSelection.BestFirst", new String[]{"-D", "1", "-N", "7"});
        as.setSearch(asSearch);
        ASEvaluation asEval = ASEvaluation.forName("weka.attributeSelection.CfsSubsetEval", new String[]{"-M"});
        as.setEvaluator(asEval);
        as.SelectAttributes(instances);
        instances = as.reduceDimensionality(instances);
        Classifier classifier = AbstractClassifier.forName("weka.classifiers.meta.AdditiveRegression", new String[]{"-S", "1", "-I", "87", "-W", "weka.classifiers.trees.RandomForest", "--", "-I", "78", "-K", "2", "-depth", "0"});
        classifier.buildClassifier(instances);

        predictLabel(labeled, classifier);
    }

    private Instances fetchData(String[] query) throws Exception {

        try (Connection con = DriverManager.getConnection("jdbc:mysql://localhost/simgrid", "root", ""); Statement st = con.createStatement()) {

            ResultSet rs = st.executeQuery("SELECT WorkLoad, NumOfMachines, " + query[1] + " FROM TrainingData");
            ResultSetMetaData md = rs.getMetaData();
            int columnCount = md.getColumnCount();

            ArrayList<Attribute> attInfo = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                attInfo.add(new Attribute(md.getColumnName(i)));
            }

            Instances dataSet = new Instances(query[0], attInfo, columnCount);
            while (rs.next()) {
                Instance inst = new DenseInstance(columnCount);
                for (int i = 1; i <= columnCount; i++) {
                    inst.setValue(i - 1, rs.getDouble(i));
                }
                dataSet.add(inst);
            }

            if (dataSet.classIndex() == -1) {
                dataSet.setClassIndex(dataSet.numAttributes() - 1);
            }

            return dataSet;
        }

    }

    private Instances generateTestSet(Instances instances) {

        int columnCount = instances.numAttributes();

        ArrayList<Attribute> attInfo = new ArrayList<>();
        for (int i = 0; i < columnCount; i++) {
            attInfo.add(instances.attribute(i));
        }

        Instances dataSet = new Instances("test", attInfo, columnCount);
        for (int i = 0; i < numOfMachines; i++) {
            Instance inst = new DenseInstance(columnCount);

            inst.setValue(0, testSet[i].get(0));
            inst.setValue(1, testSet[i].get(1));

            if (columnCount > 3) {
                int size = testSet[i].size();

                inst.setValue(2, testSet[i].get(size - 2));
                inst.setValue(3, testSet[i].get(size - 1));
            }

            inst.setValue(columnCount - 1, 0);

            dataSet.add(inst);
        }

        if (dataSet.classIndex() == -1) {
            dataSet.setClassIndex(dataSet.numAttributes() - 1);
        }

        return dataSet;
    }

    private void predictLabel(Instances labeled, Classifier classifier) throws Exception {

        Instances unlabeled = generateTestSet(labeled);

        for (int i = 0; i < unlabeled.numInstances(); i++) {
            double clsLabel = classifier.classifyInstance(unlabeled.instance(i));
            testSet[i].add(clsLabel);
        }
    }

    private void calculateCPUEnergy() {

        for (ArrayList<Double> test : testSet) {
            test.add(test.get(3) * test.get(2));
        }
    }

    private void calculateACEnergy() {

        for (ArrayList<Double> test : testSet) {
            test.add(test.get(7) * test.get(2));
        }
    }

    private ArrayList<Double> calculateTotalEnergy() {

        ArrayList<Double> optimumConfig = new ArrayList<>();

        double minEnergy = Double.MAX_VALUE;

        for (ArrayList<Double> test : testSet) {
            double energy = test.get(4) + test.get(8);
            double cpu = test.get(4);
            //double ac = test.get(8);

            if (!total && cpu < minEnergy) {
                minEnergy = cpu;
                optimumConfig = test;
            } else if (total && energy < minEnergy) {
                minEnergy = energy;
                optimumConfig = test;
            }

            test.add(energy);
        }

        return optimumConfig;
    }

    public void updateHistory(String data, double thresTemp, double envTemp) throws Exception {

        try (Connection con = DriverManager.getConnection("jdbc:mysql://localhost/simgrid", "root", "")) {

            String sqlInsert = "INSERT INTO `TrainingData` VALUES (?, ?, ?, ?, ?, ?, ?)";
            String[] output = data.split(",");

            try (PreparedStatement ps = con.prepareStatement(sqlInsert)) {

                double m = Double.valueOf(output[1]);
                double time = Double.valueOf(output[4]);
                double cpu = Double.valueOf(output[2]) / time + (numOfMachines - m) * 5;
                double ac = Double.valueOf(output[3]) / time + (numOfMachines - m) * 1;

                ps.setDouble(1, Double.valueOf(output[0])); //workload
                ps.setDouble(2, m); //numofmachines
                ps.setDouble(3, time); //responsetime
                ps.setDouble(4, cpu); //cpupower
                ps.setDouble(5, thresTemp);
                ps.setDouble(6, envTemp);
                ps.setDouble(7, ac); //acpower

                ps.execute();

            }
        }
    }

//    public static void main(String[] args) throws Exception {
//        new WekaML(210, true).runWekaML(12800);
//    }
}
