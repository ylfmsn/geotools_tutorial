package com.suntoon.tutorial.crs;

import org.geotools.data.*;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.JTS;
import org.geotools.map.FeatureLayer;
import org.geotools.map.Layer;
import org.geotools.map.MapContent;
import org.geotools.referencing.CRS;
import org.geotools.styling.SLD;
import org.geotools.styling.Style;
import org.geotools.swing.JMapFrame;
import org.geotools.swing.action.SafeAction;
import org.geotools.swing.data.JFileDataStoreChooser;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.Feature;
import org.opengis.feature.FeatureVisitor;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.util.ProgressListener;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * @ProjectionName geotools_tutorial
 * @ClassName CRSLab
 * @Description TODO
 * @Author YueLifeng
 * @Date 2019/4/3 0003上午 10:14
 * @Version 1.0
 */
public class CRSLab {
    private File sourceFile;
    private SimpleFeatureSource featureSource;
    private MapContent map;

    public static void main(String[] args) throws Exception {
        CRSLab lab = new CRSLab();
        lab.displayShapefile();
    }

    private void displayShapefile() throws Exception {
        sourceFile = JFileDataStoreChooser.showOpenFile("shp", null);
        if (sourceFile == null) {
            return;
        }
        FileDataStore store = FileDataStoreFinder.getDataStore(sourceFile);
        featureSource = store.getFeatureSource();

        // Create a map context and add our shapefile to it
        map = new MapContent();
        Style style = SLD.createSimpleStyle(featureSource.getSchema());
        Layer layer = new FeatureLayer(featureSource, style);
        map.layers().add(layer);

        // Create a JMapFrame with custom toolbar buttons
        JMapFrame mapFrame = new JMapFrame(map);
        mapFrame.enableToolBar(true);
        mapFrame.enableStatusBar(true);

        JToolBar toolbar = mapFrame.getToolBar();
        toolbar.addSeparator();
        toolbar.add(new JButton(new ValidateGeometryAction()));
        toolbar.add(new JButton(new ExportShapefileAction()));

        // Display the map frame. When it is closed the application will exit
        mapFrame.setSize(800, 600);
        mapFrame.setVisible(true);
    }

    class ValidateGeometryAction extends SafeAction {
        ValidateGeometryAction() {
            super("Validate geometry");
            putValue(Action.SHORT_DESCRIPTION, "Check each geometry");
        }

        public void action(ActionEvent e) throws Throwable {
            int numInvalid = validateFeatureGeometry(null);
            String msg;
            if (numInvalid == 0) {
                msg = "All feature geometries are valid";
            } else {
                msg = "Invalid geometries: " + numInvalid;
            }
            JOptionPane.showMessageDialog(
                    null, msg, "Geometry results", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private int validateFeatureGeometry(ProgressListener progress) throws Exception {
        final SimpleFeatureCollection featureCollection = featureSource.getFeatures();

        // Rather than use an iterator, create a FeatureVisitor to check each fature
        class ValidationVisitor implements FeatureVisitor {
            public int numInvalidGeometries = 0;

            public void visit(Feature f) {
                SimpleFeature feature = (SimpleFeature) f;
                Geometry geom = (Geometry) feature.getDefaultGeometry();
                if (geom != null && !geom.isValid()) {
                    numInvalidGeometries++;
                    System.out.println("Invalid Geoemtry: " + feature.getID());
                }
            }
        }

        ValidationVisitor visitor = new ValidationVisitor();

        // Pass visitor and the progress bar to feature collection
        featureCollection.accepts(visitor, progress);
        return visitor.numInvalidGeometries;
    }

    class ExportShapefileAction extends SafeAction {
        ExportShapefileAction() {
            super("Export...");
            putValue(Action.SHORT_DESCRIPTION, "Export using current crs");
        }

        public void action(ActionEvent e) throws Throwable {
            exportToShapefile();
        }
    }

    private void exportToShapefile() throws Exception {
        SimpleFeatureType schema = featureSource.getSchema();
        JFileDataStoreChooser chooser = new JFileDataStoreChooser("shp");
        chooser.setDialogTitle("Save reprojected shapefile");
        chooser.setSaveFile(sourceFile);
        int returnVal = chooser.showSaveDialog(null);
        if (returnVal != JFileDataStoreChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        if (file.equals(sourceFile)) {
            JOptionPane.showMessageDialog(null, "Cannot replace " + file);
            return;
        }

        CoordinateReferenceSystem dataCRS = schema.getCoordinateReferenceSystem();
        CoordinateReferenceSystem worldCRS = map.getCoordinateReferenceSystem();
        boolean lenient = true; // allow for some error due to different datums
        MathTransform transform = CRS.findMathTransform(dataCRS, worldCRS, lenient);

        SimpleFeatureCollection featureCollection = featureSource.getFeatures();

        DataStoreFactorySpi factory = new ShapefileDataStoreFactory();
        Map<String, Serializable> create = new HashMap<>();
        create.put("url", file.toURI().toURL());
        create.put("create spatial index", Boolean.TRUE);
        DataStore dataStore = factory.createNewDataStore(create);
        SimpleFeatureType featureType = SimpleFeatureTypeBuilder.retype(schema, worldCRS);
        dataStore.createSchema(featureType);

        // Get the name of the new Shapefile, which will be used to open the FeatureWriter
        String createdName = dataStore.getTypeNames()[0];

        Transaction transaction = new DefaultTransaction("Reproject");
        try (FeatureWriter<SimpleFeatureType, SimpleFeature> writer =
                     dataStore.getFeatureWriterAppend(createdName, transaction);
             SimpleFeatureIterator iterator = featureCollection.features()) {
            while (iterator.hasNext()) {
                // copy the contents of each feature and transform the geometry
                SimpleFeature feature = iterator.next();
                SimpleFeature copy = writer.next();
                copy.setAttributes(feature.getAttributes());

                Geometry geometry = (Geometry) feature.getDefaultGeometry();
                Geometry geometry2 = JTS.transform(geometry, transform);

                copy.setDefaultGeometry(geometry2);
                writer.write();
            }
            transaction.commit();
            JOptionPane.showMessageDialog(null, "Export to shapefile complete");
        } catch (Exception problem) {
            problem.printStackTrace();
            transaction.rollback();
            JOptionPane.showMessageDialog(null, "Export to shapefile failed");
        } finally {
            transaction.close();
        }
    }
}
