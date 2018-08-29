package com.tcmsoso.coolwether.fragment;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.tcmsoso.coolwether.R;
import com.tcmsoso.coolwether.db.City;
import com.tcmsoso.coolwether.db.County;
import com.tcmsoso.coolwether.db.Province;
import com.tcmsoso.coolwether.util.HttpUtil;
import com.tcmsoso.coolwether.util.Utility;

import org.litepal.LitePal;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import okhttp3.internal.Util;

public class ChooseAreaFragment extends Fragment {
    //这里是指定水平层级
    public static final int LEVEL_PROVINCE = 0;
    public static final int LEVEL_CITY = 1;
    public static final int LEVEL_COUNTY = 2;
    //获取界面上的ＵＩ
    private ProgressBar progressBar;
    private TextView titleText;
    private Button backButton;
    private ListView listView;
    //指定listView的adapter and data
    private ArrayAdapter<String> adapter;
    private List<String> dataList = new ArrayList<>();


    //省列表
    private List<Province> provinceList;
    private List<City> cityList;
    private List<County> countyList;
    //选中的省份和城市
    private Province selectedProvince;
    private City selectedCity;
    //当前的级别
    private int currentLevel;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle saveInstanceState){
        View view = inflater.inflate(R.layout.choose_area,container,false);
        titleText = (TextView) view.findViewById(R.id.title_text);
        backButton = (Button) view.findViewById(R.id.back_button);
        listView = (ListView) view.findViewById(R.id.list_view);
        adapter = new ArrayAdapter<>(getContext(),android.R.layout.simple_list_item_1,dataList);
        listView.setAdapter(adapter);
        return view;
    }

    @Override
    public void onActivityCreated(Bundle saveInstanceState){
        super.onActivityCreated(saveInstanceState);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if (currentLevel == LEVEL_PROVINCE){
                    selectedProvince = provinceList.get(i);
                    queryCities();
                } else if(currentLevel == LEVEL_CITY){
                    selectedCity = cityList.get(i);
                    queryCounties();
                }
            }
        });
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (currentLevel == LEVEL_COUNTY){
                    queryCities();
                } else if(currentLevel == LEVEL_CITY){
                    queryProvinces();
                }
            }
        });
        queryProvinces();
    }

    private void queryProvinces(){
        titleText.setText("China");
        backButton.setVisibility(View.GONE);
        provinceList = LitePal.findAll(Province.class);
        if(provinceList.size()>0){
            dataList.clear();
            for(Province province: provinceList){
                dataList.add(province.getProvinceName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel = LEVEL_PROVINCE;
        }else {
            String address = "http://guolin.tech/api/china";
            queryFromServer(address,"province");
        }
    }

    private void queryCities(){
        titleText.setText(selectedProvince.getProvinceName());
        backButton.setVisibility(View.VISIBLE);
        cityList = LitePal.where("provinceid=?",String.valueOf(selectedProvince.getId())).find(City.class);
        if (cityList.size()>0){
            dataList.clear();
            for(City city: cityList){
                dataList.add(city.getCityName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel = LEVEL_CITY;
        } else {
            int provinceCode = selectedProvince.getProvinceCode();
            String address = "http://guolin.tech/api/china/"+provinceCode;
            queryFromServer(address,"city");
        }
    }

    private void queryCounties(){
        titleText.setText(selectedCity.getCityName());
        backButton.setVisibility(View.VISIBLE);
        Log.d("ChooseAreaFragment","queryCounties");
        countyList = LitePal.where("cityid=?",String.valueOf(selectedCity.getId())).find(County.class);
        if (countyList.size() > 0){
            dataList.clear();
            for (County county : countyList){
                dataList.add(county.getCountyName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel = LEVEL_COUNTY;
        } else {
            int provinceCode = selectedProvince.getProvinceCode();
            int cityCode = selectedCity.getCityCode();
            String address = "http://guolin.tech/api/china/"+provinceCode+"/"+cityCode;
            Log.d("ChooseAreaFragment","query address: "+address);
            queryFromServer(address,"county"); //this is error at version 1.0
        }
    }

    private void queryFromServer(String address,final String type){
        showProgressBar();
        HttpUtil.sendOkHttpRequest(address, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        closeProgressBar();
                        Toast.makeText(getContext(),"reload fail",Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseText = response.body().string();
                boolean result = false;
                if("province".equals(type)){
                    result= Utility.handleProvinceResponse(responseText);
                } else if ("city".equals(type)){
                    result = Utility.handleCityResponse(responseText,selectedProvince.getId());
                } else if ("county".equals(type)){
                    Log.d("ChooseAreaFragment","this is queryFromServer");
                    result= Utility.handleCountyResponse(responseText,selectedCity.getId());
                }


                if(result){
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            closeProgressBar();
                            if("province".equals(type)){
                                queryProvinces();
                            } else if ("city".equals(type)){
                                queryCities();
                            } else if ("county".equals(type)){
                                queryCounties();
                            }
                        }
                    });
                }
            }
        });
    }

    //show progressbar
    private void showProgressBar(){
        if(progressBar == null){
            progressBar = getActivity().findViewById(R.id.progress_bar);
            progressBar.setProgress(5);
        }
        progressBar.setVisibility(View.VISIBLE);
    }

    private void closeProgressBar(){
        if (progressBar != null){
            progressBar.setVisibility(View.GONE);
        }
    }



}
