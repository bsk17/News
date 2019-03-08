package com.example.hp.news;


import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    // creating an array list to store the titles and content of news
    ArrayList<String> titles = new ArrayList<>();
    ArrayList<String> content = new ArrayList<>();
    ArrayAdapter arrayAdapter;
    // creating object of database class
    SQLiteDatabase newsData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        // setting up the database ArticlesDATA to hold the content
        newsData = this.openOrCreateDatabase("ArticlesDATA",MODE_PRIVATE,null);
        // table name will be Articles
        newsData.execSQL("CREATE TABLE IF NOT EXISTS Articles (id INTEGER PRIMARY KEY, articleId INTEGER, title VARCHAR, content VARCHAR)");


        // creating an object of the class
        Downloadtask task = new Downloadtask();
        try{
            // this is the api address of hacker news top stories which will get executed
            task.execute("https://hacker-news.firebaseio.com/v0/topstories.json?print=pretty");
        }catch(Exception e){
            e.printStackTrace();
        }

        ListView listView = findViewById(R.id.listView);
        arrayAdapter= new ArrayAdapter(this,android.R.layout.simple_list_item_1,titles);
        listView.setAdapter(arrayAdapter);

        // when we click on an any item on the content array it will move to the next activity
        // which has a webView
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent = new Intent(getApplicationContext(),NewsArticleActivity.class);
                intent.putExtra("content",content.get(position));

                startActivity(intent);
            }
        });

        updateList();
    }


    // we are creating this method to update the listview with the contents from the database
    public void updateList(){
        Cursor c = newsData.rawQuery("SELECT * FROM Articles",null);
        int contentIndex = c.getColumnIndex("content");
        int titleIndex = c.getColumnIndex("title");

        if(c.moveToFirst()){
            titles.clear();
            content.clear();

            do {
                titles.add(c.getString(titleIndex));
                content.add(c.getString(contentIndex));
            }while(c.moveToNext());

            arrayAdapter.notifyDataSetChanged();
        }

    }

    // we are creating 3 urls and all the connections
    // 1 for getting ids using api
    // 2 for getting particular json object of single id using api
    // 3 for loading the actual news content from link

    // creating a class which will download the content form the api
    public class Downloadtask extends AsyncTask<String, Void , String>{

        @Override
        // from task.execute the url goes to the urls below
        protected String doInBackground(String... urls) {

            // first url
            // this result will contain all the ids
            String result = "";
            URL url;
            HttpURLConnection httpURLConnection = null;

            try
            { url = new URL(urls[0]);

              httpURLConnection = (HttpURLConnection) url.openConnection();

              InputStream inputStream = httpURLConnection.getInputStream();

              InputStreamReader inputStreamReader = new InputStreamReader(inputStream);

              int data = inputStreamReader.read();

              while(data != -1) {
                  char current = (char) data;
                  result += current;
                  data = inputStreamReader.read();
              }

              // we are inserting all the id we get from the api into a json array so that we can
                // extract one by one
              JSONArray jsonArray = new JSONArray(result);

              int noofitems = 5;

              if (jsonArray.length() < 5){
                  noofitems = jsonArray.length();
              }

              // we have to delete the data each time before loading the app so
              newsData.execSQL("DELETE FROM Articles");

              for (int i=0; i < noofitems; i++){
                  String articleId = jsonArray.getString(i);

                  // second url
                  // this is again an api address given for a specific article using article id
                  url = new URL("https://hacker-news.firebaseio.com/v0/item/"+ articleId + ".json?print=pretty");

                  httpURLConnection = (HttpURLConnection) url.openConnection();

                  inputStream = httpURLConnection.getInputStream();

                  inputStreamReader = new InputStreamReader(inputStream);

                  data = inputStreamReader.read();

                  // this will contain info about particular news
                  String articleInfo = "";

                  while(data != -1) {
                      char current = (char) data;

                      articleInfo += current;

                      data = inputStreamReader.read();
                  }

                  // we convert all the information we get into a Json object
                  JSONObject jsonObject = new JSONObject(articleInfo);

                  // this is to make sure that we get a title and url for each news
                  if (!jsonObject.isNull("title") && !jsonObject.isNull("url")){
                      String articleTitle = jsonObject.getString("title");

                      String articleUrl = jsonObject.getString("url");

                      // third url
                      url = new URL(articleUrl);

                      httpURLConnection = (HttpURLConnection) url.openConnection();

                      inputStream = httpURLConnection.getInputStream();

                      inputStreamReader = new InputStreamReader(inputStream);

                      data = inputStreamReader.read();

                      // this will be the actual content which is in html format
                      // the complete news
                      String articleContent = "";

                      while(data != -1) {
                          char current = (char) data;

                          articleContent += current;

                          data = inputStreamReader.read();
                      }

                      // creating a general statement for sql functions
                      String sql = "INSERT INTO Articles (articleId, title, content) VALUES (?, ?, ?)";
                      SQLiteStatement statement = newsData.compileStatement(sql);

                      // substituting the values from json into database
                      statement.bindString(1, articleId);
                      statement.bindString(2, articleTitle);
                      statement.bindString(3, articleContent);
                      statement.execute();

                  }
              }

              return result;
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            updateList();
        }
    }
}
