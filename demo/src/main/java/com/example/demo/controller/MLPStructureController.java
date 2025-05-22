package com.example.demo.controller;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import weka.classifiers.Evaluation;
import weka.classifiers.functions.MultilayerPerceptron;
import weka.core.Instances;
import weka.core.converters.CSVLoader;

@RestController
@RequestMapping("/api/analyze")
public class MLPStructureController {

    @CrossOrigin(origins = {"http://127.0.0.1:5501", "http://localhost:5501"})
    @PostMapping("/mlp-structure")
    public Map<String, Object> obtenerEstructura(@RequestParam("file") MultipartFile file,
                                               @RequestParam(value = "hiddenLayers", defaultValue = "8") String hiddenLayers,
                                               @RequestParam(value = "trainingTime", defaultValue = "500") int trainingTime,
                                               @RequestParam(value = "learningRate", defaultValue = "0.3") double learningRate,
                                               @RequestParam(value = "momentum", defaultValue = "0.2") double momentum,
                                               @RequestParam(value = "validation", defaultValue = "train") String validationMethod) {
        try {
            // Cargar datos
            Instances data;
            String fileName = file.getOriginalFilename();
            if (fileName != null && fileName.endsWith(".csv")) {
                CSVLoader loader = new CSVLoader();
                loader.setSource(file.getInputStream());
                data = loader.getDataSet();
            } else {
                data = new Instances(new InputStreamReader(file.getInputStream()));
            }

            // Índice de clase
            if (data.classIndex() == -1)
                data.setClassIndex(data.numAttributes() - 1);

            // Configurar MLP
            MultilayerPerceptron mlp = new MultilayerPerceptron();
            mlp.setTrainingTime(trainingTime);
            mlp.setLearningRate(learningRate);
            mlp.setMomentum(momentum);
            mlp.setHiddenLayers(hiddenLayers); // Permitir configuración personalizada
            
            // Construir el modelo
            mlp.buildClassifier(data);

            // Evaluación del modelo
            Evaluation eval = new Evaluation(data);
            
            if ("cross-validation".equalsIgnoreCase(validationMethod)) {
                eval.crossValidateModel(mlp, data, 10, new java.util.Random(1));
            } else {
                eval.evaluateModel(mlp, data);
            }

            // Información de la estructura de la red
            List<String> entradas = new ArrayList<>();
            for (int i = 0; i < data.numAttributes() - 1; i++) {
                entradas.add(data.attribute(i).name());
            }

            List<String> salidas = new ArrayList<>();
            for (int i = 0; i < data.classAttribute().numValues(); i++) {
                salidas.add(data.classAttribute().value(i));
            }

            // Información detallada del modelo
            Map<String, Object> modelInfo = new HashMap<>();
            modelInfo.put("learning_rate", mlp.getLearningRate());
            modelInfo.put("momentum", mlp.getMomentum());
            modelInfo.put("epochs", mlp.getTrainingTime());
            modelInfo.put("hidden_layers", hiddenLayers);
            modelInfo.put("batch_size", mlp.getBatchSize());
            modelInfo.put("decay", mlp.getDecay());
            modelInfo.put("validation_method", validationMethod);

            // Métricas de evaluación
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("error_rate", eval.errorRate());
            metrics.put("correct", eval.correct());
            metrics.put("incorrect", eval.incorrect());
            metrics.put("kappa", eval.kappa());
            metrics.put("mean_absolute_error", eval.meanAbsoluteError());
            metrics.put("root_mean_squared_error", eval.rootMeanSquaredError());
            metrics.put("relative_absolute_error", eval.relativeAbsoluteError());
            metrics.put("root_relative_squared_error", eval.rootRelativeSquaredError());
            metrics.put("unclassified", eval.unclassified());
            metrics.put("area_under_roc", eval.areaUnderROC(0)); // Para clasificación binaria
            metrics.put("precision", eval.precision(0));
            metrics.put("recall", eval.recall(0));
            metrics.put("f_measure", eval.fMeasure(0));
            
            // Matriz de confusión
            double[][] confusionMatrix = eval.confusionMatrix();
            List<List<Integer>> confusionMatrixList = new ArrayList<>();
            for (double[] row : confusionMatrix) {
                List<Integer> rowList = new ArrayList<>();
                for (double val : row) {
                    rowList.add((int) val);
                }
                confusionMatrixList.add(rowList);
            }
            metrics.put("confusion_matrix", confusionMatrixList);

            // Informe de clasificación por clase
            List<Map<String, Object>> classDetails = new ArrayList<>();
            for (int i = 0; i < data.classAttribute().numValues(); i++) {
                Map<String, Object> classInfo = new HashMap<>();
                classInfo.put("class_name", data.classAttribute().value(i));
                classInfo.put("precision", eval.precision(i));
                classInfo.put("recall", eval.recall(i));
                classInfo.put("f_measure", eval.fMeasure(i));
                classInfo.put("area_under_roc", eval.areaUnderROC(i));
                classDetails.add(classInfo);
            }
            metrics.put("class_details", classDetails);

            return Map.of(
                "structure", Map.of(
                    "inputs", entradas,
                    "hidden_layers", hiddenLayers,
                    "outputs", salidas
                ),
                "model_info", modelInfo,
                "evaluation_metrics", metrics,
                "summary", eval.toSummaryString("\nResumen del Modelo\n", true),
                "class_details_report", eval.toClassDetailsString(),
                "confusion_matrix_report", eval.toMatrixString()
            );

        } catch (Exception e) {
            e.printStackTrace();
            return Map.of("error", e.getMessage());
        }
    }
}