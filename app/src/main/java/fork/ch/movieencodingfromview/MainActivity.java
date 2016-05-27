package fork.ch.movieencodingfromview;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        findViewById(R.id.encode).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MediaCodevVideoExporter mediaCodevVideoExporter = new MediaCodevVideoExporter(MainActivity.this);
                try {
                    mediaCodevVideoExporter.createVideoUsingMediaCodec( findViewById(R.id.textView));
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });



    }

}
