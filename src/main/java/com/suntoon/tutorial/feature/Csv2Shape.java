package com.suntoon.tutorial.feature;

import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.swing.data.JFileDataStoreChooser;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @ProjectionName geotools_tutorial
 * @ClassName Csv2Shape
 * @Description 从逗号分隔的csv文件中读取点位置和关联的属性数据，然后将数据以shapefile格式导出，
 *              说明如何构建要素类型
 * @Author YueLifeng
 * @Date 2019/4/3 0003上午 10:16
 * @Version 1.0
 */
public class Csv2Shape {
    public static void main(String[] args) throws Exception{
        //设置跨平台的外观和兼容性
        UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());

        File file = JFileDataStoreChooser.showOpenFile("csv", null);
        if (file == null) {
            return;
        }

        //我们创建一个FeatureType来描述我们从CSV文件导入的数据并写入shapefile
        final SimpleFeatureType TYPE = DataUtilities.createType(
                "Location",
                "the_geom:Point:sric=4326,"    //几何对象的属性：点类型
                        + "name:String,"    //一个字符属性
                        + "number:Integer"   //一个数字属性
        );

        System.out.println("TYPE:" + TYPE);

        /**
         * 读取CSV文件并为每条记录创建一个功能
         * 1、用GeometryFactory创建新点集
         * 2、使用SimpleFeatureBuilder创建要素集（SimpleFeature对象）
         */
        //存放创建的要素的列表
        List<SimpleFeature> features = new ArrayList<>();
        //GeometryFactory将用于创建每个要素的几何属性，用作该位置的Point对象
        GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();

        SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(TYPE);

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            //数据文件的第一行是标题
            String line = reader.readLine();
            System.out.println("Header: " + line);

            for (line = reader.readLine(); line != null; line = reader.readLine()) {
                if (line.trim().length() > 0) {    //跳过空白行
                    String tokens[] = line.split("\\,");

                    double latitude = Double.parseDouble(tokens[0]);
                    double longitude = Double.parseDouble(tokens[1]);
                    String name = tokens[2].trim();
                    int number = Integer.parseInt(tokens[3].trim());

                    //Longitude(= x coord) 在前面
                    Point point = geometryFactory.createPoint(new Coordinate(longitude, latitude));

                    featureBuilder.add(point);
                    featureBuilder.add(name);
                    featureBuilder.add(number);
                    SimpleFeature feature = featureBuilder.buildFeature(null);
                    features.add(feature);
                }
            }
        }

        /**
         * 从FeatureCollection创建shapefile
         * 1、用带有一个参数的DataStoreFactory来表明要建一个空间索引
         * 2、使用createSchema（SimpleFeatureType）方法设置shapefile
         */
        //获取输出文件名并创建新的shapefile
        File newFile = getNewShapeFile(file);

        ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();

        Map<String, Serializable> params = new HashMap<>();
        params.put("url", newFile.toURI().toURL());
        params.put("create spatial index", Boolean.TRUE);

        ShapefileDataStore newDataStore = (ShapefileDataStore) dataStoreFactory.createNewDataStore(params);

        //TYPE用作描述文件内容的模板
        newDataStore.createSchema(TYPE);

        /**
         * 将要素数据写入shapefile
         */
        Transaction transaction = new DefaultTransaction("create");

        String typeName = newDataStore.getTypeNames()[0];
        SimpleFeatureSource featureSource = newDataStore.getFeatureSource(typeName);
        SimpleFeatureType SHAPE_TYPE = featureSource.getSchema();

        /**
         * Shapefile格式用几个限制：
         * 1、“the_geom” 始终是第一个，用于几何属性名称
         * 2、“the_geom” 必须是Point，MultiPoint，MuiltiLineString，MultiPolygon类型
         * 3、属性名称的长度有限
         * 4、并非所有数据类型都受支持（比如时间戳表示的日期）
         *
         * 每个数据存储都有不同的限制，因此请检查生成的SimpleFeatureType
         */
        System.out.println("SHAPE: " + SHAPE_TYPE);

        if (featureSource instanceof SimpleFeatureType) {
            SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;

            /**
             * SimpleFeatureStore有一个可以从SimpleFeatureCollection对象中添加featues的方法
             * 因此我们可以使用ListFeatureCollection类包装我们的功能列表
             */
            SimpleFeatureCollection collection = new ListFeatureCollection(TYPE, features);
            featureStore.setTransaction(transaction);
            try {
                featureStore.addFeatures(collection);
                transaction.commit();
            } catch (Exception problem) {
                problem.printStackTrace();
                transaction.rollback();
            } finally {
                transaction.close();
            }
            System.exit(0);
        } else {
            System.out.println(typeName + " does not support read/write access");
            System.exit(1);
        }
    }

    /**
     * @Author YueLifeng
     * @Description //提示用户输入shapefile的名称和路径
     * @Date 下午 2:30 2019/4/2 0002
     * @param csvFile 用于创建默认shapefile名称的输入csv文件
     * @return java.io.File
     */
    private static File getNewShapeFile(File csvFile) {
        String path = csvFile.getAbsolutePath();
        String newPath = path.substring(0, path.length() - 4) + ".shp";

        JFileDataStoreChooser chooser = new JFileDataStoreChooser("shp");
        chooser.setDialogTitle("Save shapefile");
        chooser.setSelectedFile(new File(newPath));

        int returnVal = chooser.showSaveDialog(null);

        if (returnVal != JFileDataStoreChooser.APPROVE_OPTION) {
            //用户取消
            System.exit(0);
        }

        File newFile = chooser.getSelectedFile();
        if (newFile.equals(csvFile)) {
            System.out.println("错误: 不能替换" + csvFile);
            System.exit(0);
        }
        return newFile;
    }
}
