package com.example.demo.controller;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.util.J48TreeParser;
import com.example.demo.util.J48TreeParser.TreeNode;

import weka.classifiers.Evaluation;
import weka.classifiers.functions.MultilayerPerceptron;
import weka.classifiers.trees.J48;
import weka.clusterers.SimpleKMeans;
import weka.core.Instances;
import weka.core.converters.CSVLoader;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Remove;




@RestController
@RequestMapping("/api/analyze")
public class WekaController {
    @CrossOrigin(origins = {"http://127.0.0.1:5501", "http://localhost:5501"})
    @PostMapping("/upload")
    public String analyzeFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("method") String method,
            @RequestParam(value = "evaluation", required = false) String evaluationMethod) {
        try {
            // Determinar el tipo de archivo (CSV o ARFF) y cargar los datos adecuadamente
            Instances data;
            String fileName = file.getOriginalFilename();
            if (fileName != null && fileName.endsWith(".csv")) {
                data = loadCSV(file.getInputStream());
            } else {
                data = loadARFF(file.getInputStream());
            }

            // Establecer el índice de la clase, si existe
            if (data.classIndex() == -1 && data.numAttributes() > 1) {
                data.setClassIndex(data.numAttributes() - 1);
            }

            // Llamar al método correspondiente
            String result;
            switch (method) {
                case "clustering":
                    result = performClustering(data);
                    break;
                case "classification":
                    result = performClassification(data, evaluationMethod);
                    break;
                case "kMeans":
                    result = performKMeans(data);
                    break;
                default:
                    result = "Método no reconocido.";
            }

            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return "Error durante el análisis: " + e.getMessage();
        }
    }



    @PostMapping("/tree")
    @ResponseBody
    public TreeNode obtenerArbolJ48(@RequestParam("file") MultipartFile file) {
        try {
            Instances data;
            String fileName = file.getOriginalFilename();
            if (fileName != null && fileName.endsWith(".csv")) {
                data = loadCSV(file.getInputStream());
            } else {
                data = loadARFF(file.getInputStream());
            }
    
            if (data.classIndex() == -1 && data.numAttributes() > 1) {
                data.setClassIndex(data.numAttributes() - 1);
            }
    
            J48 j48 = new J48();
            j48.buildClassifier(data);
    
            String treeText = j48.toString();
    
            return J48TreeParser.parse(treeText);
    
        } catch (Exception e) {
            e.printStackTrace();
            return new TreeNode("Error: " + e.getMessage());
        }
    }


    @PostMapping("/dot")
public String obtenerDot(@RequestParam("file") MultipartFile file) {
    try {
        Instances data = file.getOriginalFilename().endsWith(".csv")
            ? loadCSV(file.getInputStream())
            : loadARFF(file.getInputStream());

        if (data.classIndex() == -1)
            data.setClassIndex(data.numAttributes() - 1);

        J48 j48 = new J48();
        j48.buildClassifier(data);

        return j48.graph(); // <-- formato DOT
    } catch (Exception e) {
        e.printStackTrace();
        return "Error al generar el árbol DOT: " + e.getMessage();
    }
}

@CrossOrigin(origins = {"http://127.0.0.1:5501", "http://localhost:5501"})
@PostMapping("/mlp")
public @ResponseBody Map<String, String> analizarMLP(
    @RequestParam("file") MultipartFile file,
    @RequestParam(value = "evaluation", required = false, defaultValue = "use-training-set") String evaluationMethod
) {
    Map<String, String> response = new HashMap<>();
    try {
        System.setProperty("java.awt.headless", "true");

        // Detectar tipo de archivo y cargar
        Instances data;
        String fileName = file.getOriginalFilename();
        if (fileName != null && fileName.endsWith(".csv")) {
            CSVLoader loader = new CSVLoader();
            loader.setSource(file.getInputStream());
            data = loader.getDataSet();
        } else {
            data = new Instances(new InputStreamReader(file.getInputStream()));
        }

        // Establecer clase objetivo
        if (data.classIndex() == -1) {
            data.setClassIndex(data.numAttributes() - 1);
        }

        // Configurar y entrenar MLP (sin -G ni -R)
        MultilayerPerceptron mlp = new MultilayerPerceptron();
        mlp.setOptions(weka.core.Utils.splitOptions("-L 0.3 -M 0.2 -N 500 -H a"));
        mlp.buildClassifier(data);

        // Evaluación
        Evaluation eval = new Evaluation(data);
        if ("cross-validation".equalsIgnoreCase(evaluationMethod)) {
            eval.crossValidateModel(mlp, data, 10, new java.util.Random(1));
        } else {
            eval.evaluateModel(mlp, data);
        }

        // Respuesta
        StringBuilder evaluacion = new StringBuilder();
        evaluacion.append("=== Evaluation Summary ===\n");
        evaluacion.append(eval.toSummaryString()).append("\n");
        evaluacion.append("=== Detailed Accuracy By Class ===\n");
        evaluacion.append(eval.toClassDetailsString()).append("\n");
        evaluacion.append("=== Confusion Matrix ===\n");
        evaluacion.append(eval.toMatrixString()).append("\n");

        response.put("modelo", mlp.toString());
        response.put("evaluacion", evaluacion.toString());

    } catch (Exception e) {
        e.printStackTrace();
        response.put("error", "Error al procesar el archivo: " + e.getMessage());
    }

    return response;
}



@PostMapping("/kmeans-plot")
public List<Map<String, Object>> obtenerPuntosClusterConEjes(
        @RequestParam("file") MultipartFile file,
        @RequestParam("xAttr") int xAttr,
        @RequestParam("yAttr") int yAttr,
        @RequestParam("k") int k) {
    try {
        Instances data = file.getOriginalFilename().endsWith(".csv")
            ? loadCSV(file.getInputStream())
            : loadARFF(file.getInputStream());

        // Validar tipo numérico
        if (!data.attribute(xAttr).isNumeric() || !data.attribute(yAttr).isNumeric()) {
            throw new IllegalArgumentException("Los atributos seleccionados deben ser numéricos.");
        }

        // Validar K
        if (k < 1 || k > data.numInstances()) {
            throw new IllegalArgumentException("El número de clusters (k) debe ser entre 1 y el total de instancias.");
        }

        SimpleKMeans kMeans = new SimpleKMeans();
        kMeans.setNumClusters(k);
        kMeans.buildClusterer(data);

        List<Map<String, Object>> puntos = new ArrayList<>();
        for (int i = 0; i < data.numInstances(); i++) {
            Map<String, Object> punto = new HashMap<>();
            punto.put("x", data.instance(i).value(xAttr));
            punto.put("y", data.instance(i).value(yAttr));
            punto.put("cluster", kMeans.clusterInstance(data.instance(i)));
            puntos.add(punto);
        }

        return puntos;
    } catch (IllegalArgumentException e) {
        return List.of(Map.of("error", e.getMessage()));
    } catch (Exception e) {
        e.printStackTrace();
        return List.of(Map.of("error", "Error durante el clustering: " + e.getMessage()));
    }
}



        









    private Instances loadCSV(InputStream inputStream) throws Exception {
        CSVLoader loader = new CSVLoader();
        loader.setSource(inputStream);
        return loader.getDataSet();
    }

    private Instances loadARFF(InputStream inputStream) throws Exception {
        return new Instances(new InputStreamReader(inputStream));
    }

    private String performClustering(Instances data) {
        try {
            // Eliminar el atributo de clase antes de clustering
            Remove remove = new Remove();
            remove.setAttributeIndices(String.valueOf(data.classIndex() + 1)); // WEKA usa índices 1-based
            remove.setInputFormat(data);
            Instances dataWithoutClass = Filter.useFilter(data, remove);

            // Configurar y aplicar K-Means
            SimpleKMeans kMeans = new SimpleKMeans();
            kMeans.setNumClusters(3); // Número de clusters deseado
            kMeans.buildClusterer(dataWithoutClass);

            // Construir resultados
            StringBuilder result = new StringBuilder("Resultados del Clustering:\n");
            for (int i = 0; i < dataWithoutClass.numInstances(); i++) {
                int cluster = kMeans.clusterInstance(dataWithoutClass.instance(i));
                result.append("Instancia ").append(i).append(" en Cluster ").append(cluster).append("\n");
            }
            return result.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "Error al realizar el clustering: " + e.getMessage();
        }
    }

    private String performKMeans(Instances data) {
        try {
            // Eliminar el atributo de clase antes de clustering
            Remove remove = new Remove();
            remove.setAttributeIndices(String.valueOf(data.classIndex() + 1)); // WEKA usa índices 1-based
            remove.setInputFormat(data);
            Instances dataWithoutClass = Filter.useFilter(data, remove);

            // Configurar y aplicar K-Means
            SimpleKMeans kMeans = new SimpleKMeans();
            kMeans.setNumClusters(2); // Número de clusters deseado
            kMeans.buildClusterer(dataWithoutClass);

            // Crear el resultado del análisis
            StringBuilder result = new StringBuilder("kMeans\n======\n\n");

            result.append("Number of clusters: ").append(kMeans.getNumClusters()).append("\n");
            result.append("Within cluster sum of squared errors: ").append(kMeans.getSquaredError()).append("\n\n");

            result.append("Final cluster centroids:\n");
            Instances centroids = kMeans.getClusterCentroids();
            for (int i = 0; i < centroids.numInstances(); i++) {
                result.append("Cluster ").append(i).append(": ");
                for (int j = 0; j < centroids.numAttributes(); j++) {
                    result.append(centroids.instance(i).value(j)).append(", ");
                }
                result.append("\n");
            }

            // Calcular manualmente el tamaño de los clústeres
            int[] clusterSizes = new int[kMeans.getNumClusters()];
            for (int i = 0; i < dataWithoutClass.numInstances(); i++) {
                int cluster = kMeans.clusterInstance(dataWithoutClass.instance(i));
                clusterSizes[cluster]++;
            }

            result.append("\nClustered Instances:\n");
            for (int i = 0; i < clusterSizes.length; i++) {
                result.append("Cluster ").append(i).append(": ").append(clusterSizes[i])
                      .append(" (").append((clusterSizes[i] * 100.0 / dataWithoutClass.numInstances())).append("%)\n");
            }

            // Contar las instancias incorrectas
            double incorrectCount = 0;
            for (int i = 0; i < data.numInstances(); i++) {
                // Obtener el clúster al que se asigna la instancia
                int cluster = kMeans.clusterInstance(dataWithoutClass.instance(i));

                // Obtener la clase real de la instancia
                double realClassValue = data.instance(i).classValue();

                // Si el clúster asignado no coincide con la clase real, se considera incorrecto
                if (cluster != realClassValue) {
                    incorrectCount++;
                }
            }

            // Mostrar la cantidad de instancias incorrectamente clasificadas
            result.append("\nIncorrectly clustered instances: ").append(incorrectCount).append(" (")
                  .append((incorrectCount / data.numInstances()) * 100).append("%)\n");

            return result.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "Error al realizar K-Means: " + e.getMessage();
        }
    }

    private String performClassification(Instances data, String evaluationMethod) {
        try {
            // Crear un clasificador J48 (C4.5)
            J48 j48 = new J48();
            j48.buildClassifier(data);

            // Evaluar el modelo
            Evaluation eval;
            if ("cross-validation".equalsIgnoreCase(evaluationMethod)) {
                eval = new Evaluation(data);
                eval.crossValidateModel(j48, data, 10, new java.util.Random(1));
            } else { // Por defecto: usar conjunto de entrenamiento
                eval = new Evaluation(data);
                eval.evaluateModel(j48, data);
            }

            // Crear un StringBuilder para los resultados
            StringBuilder result = new StringBuilder("Resultados de la Clasificación:\n");

            // Resultado de la evaluación general
            result.append(eval.toSummaryString("\nResultados de la Evaluación\n", false));

            // Detalles de la precisión por clase
            result.append("\n\n=== Detailed Accuracy By Class ===\n");
            result.append(eval.toClassDetailsString());

            // Matriz de confusión
            result.append("\n\n=== Confusion Matrix ===\n");
            result.append(eval.toMatrixString());

            return result.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "Error al realizar la clasificación: " + e.getMessage();
        }
    }
}