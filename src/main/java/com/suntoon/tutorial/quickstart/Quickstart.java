package com.suntoon.tutorial.quickstart;

import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.map.FeatureLayer;
import org.geotools.map.Layer;
import org.geotools.map.MapContent;
import org.geotools.styling.SLD;
import org.geotools.styling.Style;
import org.geotools.swing.JMapFrame;
import org.geotools.swing.data.JFileDataStoreChooser;

import java.io.File;

/**
 * @ProjectionName geotools_tutorial
 * @ClassName Quickstart
 * @Description 提示用户输入shp，并显示
 * @Author YueLifeng
 * @Date 2019/4/3 0003上午 10:13
 * @Version 1.0
 */
public class Quickstart {
    public static void main(String[] args) throws Exception {

        File file = JFileDataStoreChooser.showOpenFile("shp", null);

        if (file == null) {
            return;
        }

        FileDataStore store = FileDataStoreFinder.getDataStore(file);
        SimpleFeatureSource featureSource = store.getFeatureSource();

        //创建地图框并显示shp
        MapContent mapContent = new MapContent();
        mapContent.setTitle("地图");

        Style style = SLD.createSimpleStyle(featureSource.getSchema());
        Layer layer = new FeatureLayer(featureSource, style);    //从磁盘直接读取数据
        mapContent.addLayer(layer);

        //显示地图
        JMapFrame.showMap(mapContent);
    }
}
