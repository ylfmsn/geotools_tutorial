package com.suntoon.tutorial;

import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFactorySpi;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.Query;
import org.geotools.data.postgis.PostgisNGDataStoreFactory;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.filter.text.cql2.CQL;
import org.geotools.swing.action.SafeAction;
import org.geotools.swing.data.JDataStoreWizard;
import org.geotools.swing.table.FeatureCollectionTableModel;
import org.geotools.swing.wizard.JWizard;
import org.opengis.feature.type.FeatureType;
import org.opengis.filter.Filter;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Map;

/**
 * @ProjectionName geotools_tutorial
 * @ClassName QueryLab
 * @Description TODO
 * @Author YueLifeng
 * @Date 2019/4/3 0003上午 10:17
 * @Version 1.0
 */
public class QueryLab extends JFrame {
    private DataStore dataStore;
    private JComboBox<String> featureTypeCBox;
    private JTable table;
    private JTextField text;

    public QueryLab() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setLayout(new BorderLayout());

        text = new JTextField(80);
        text.setText("include");
        getContentPane().add(text, BorderLayout.NORTH);

        table = new JTable();
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setModel(new DefaultTableModel(5, 5));
        table.setPreferredScrollableViewportSize(new Dimension(500, 200));

        JScrollPane scrollPane = new JScrollPane(table);
        getContentPane().add(scrollPane, BorderLayout.CENTER);

        JMenuBar menuBar = new JMenuBar();
        setJMenuBar(menuBar);

        JMenu fileMenu = new JMenu("file");
        menuBar.add(fileMenu);

        featureTypeCBox = new JComboBox<>();
        menuBar.add(featureTypeCBox);

        JMenu dataMenu = new JMenu("Data");
        menuBar.add(dataMenu);
        pack();

        fileMenu.add(
                new SafeAction("Open shapefile...") {
                    @Override
                    public void action(ActionEvent e) throws Throwable {
                        connect(new ShapefileDataStoreFactory());
                    }
                });

        fileMenu.add(new SafeAction("Connection to PostGIS database...") {
            @Override
            public void action(ActionEvent e) throws Throwable {
                connect(new PostgisNGDataStoreFactory());
            }
        });

        fileMenu.add(new SafeAction("Connect to DataStore...") {
            @Override
            public void action(ActionEvent e) throws Throwable {
                connect(null);
            }
        });

        fileMenu.addSeparator();
        fileMenu.add(new SafeAction("Exit") {
            @Override
            public void action(ActionEvent e) throws Throwable {
                System.exit(0);
            }
        });

        dataMenu.add(new SafeAction("Get features") {
            @Override
            public void action(ActionEvent e) throws Throwable {
                filterFeatures();
            }
        });

        dataMenu.add(new SafeAction("Count") {
            @Override
            public void action(ActionEvent e) throws Throwable {
                countFeatures();
            }
        });

        dataMenu.add(new SafeAction("Geometry") {
            @Override
            public void action(ActionEvent e) throws Throwable {
                queryFeatures();
            }
        });
    }

    private void queryFeatures() throws Exception {
        String typeName = (String) featureTypeCBox.getSelectedItem();
        SimpleFeatureSource source = dataStore.getFeatureSource(typeName);

        FeatureType schema = source.getSchema();
        String name = schema.getGeometryDescriptor().getLocalName();

        Filter filter = CQL.toFilter(text.getText());

        Query query = new Query(typeName, filter, new String[]{name});

        SimpleFeatureCollection features = source.getFeatures(query);

        FeatureCollectionTableModel model = new FeatureCollectionTableModel(features);
        table.setModel(model);
    }

    private void countFeatures() throws Exception {
        String typeName = (String) featureTypeCBox.getSelectedItem();
        SimpleFeatureSource source = dataStore.getFeatureSource(typeName);

        Filter filter = CQL.toFilter(text.getText());
        SimpleFeatureCollection features = source.getFeatures(filter);

        int count = features.size();
        JOptionPane.showMessageDialog(text, "Number of selected features: " + count);
    }

    private void filterFeatures() throws Exception {
        String typeName = (String) featureTypeCBox.getSelectedItem();
        SimpleFeatureSource source = dataStore.getFeatureSource(typeName);

        Filter filter = CQL.toFilter(text.getText());
        SimpleFeatureCollection features = source.getFeatures(filter);
        FeatureCollectionTableModel model = new FeatureCollectionTableModel(features);
        table.setModel(model);
    }

    private void connect(DataStoreFactorySpi format) throws Exception {
        JDataStoreWizard wizard = new JDataStoreWizard(format);
        int result = wizard.showModalDialog();
        if (result == JWizard.FINISH) {
            Map<String, Object> connectionParameters = wizard.getConnectionParameters();
            dataStore = DataStoreFinder.getDataStore(connectionParameters);
            if (dataStore == null) {
                JOptionPane.showMessageDialog(null, "Could not connect - check parameters");
            }
            updateUI();
        }
        ;    }

    private void updateUI() throws Exception {
        ComboBoxModel<String> cbm = new DefaultComboBoxModel<>(dataStore.getTypeNames());
        featureTypeCBox.setModel(cbm);

        table.setModel(new DefaultTableModel(5, 5));
    }


    public static void main(String[] args) {
        JFrame frame = new QueryLab();
        frame.setVisible(true);
    }
}
