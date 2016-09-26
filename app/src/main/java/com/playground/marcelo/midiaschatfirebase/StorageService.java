package com.playground.marcelo.midiaschatfirebase;

import android.annotation.TargetApi;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@RequiresApi(api = Build.VERSION_CODES.HONEYCOMB)
public class StorageService extends Service {

    //Status de envio
    private final int STATUS_START = 0;
    private final int STATUS_STOP = 1;
    private final int STATUS_ERRO = 2;
    private final int STATUS_SUCESS = 3;

    //Salvando Ids das notificações
    private Map<Integer, Boolean> notifyIds;

    //Referencias de serviços Firebase
    private FirebaseAuth auth;
    private FirebaseStorage storage;
    private StorageReference storageReference;
    private FirebaseDatabase database;
    private DatabaseReference databaseReference;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        //Inializando HasMap de notificacoes
        notifyIds = new HashMap<>();
        //Preparando storage
        storage = FirebaseStorage.getInstance();
        storageReference = storage.getReferenceFromUrl("gs://storage-71a19.appspot.com");
        //Preparando base de dados
        database = FirebaseDatabase.getInstance();
        databaseReference = database.getReference("ChatFirebaseDomain");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        for (int i = 1; i <= notifyIds.size(); i++)
            if (notifyIds.get(i))
                createNotification(i, STATUS_STOP);//cancelando todas notificacoes que estiverem enviando
    }

    @Override
    public void onStart(Intent intent, final int startId) {
        super.onStart(intent, startId);

        if (intent != null) {
            final Bundle extras = intent.getExtras();//Recupera parametros intent
            servicesRunanble(startId, true);//Inicia finalizada.
            createNotification(startId, STATUS_START);//Notifica start no envio

            File file = new File((String) extras.get("arquivo"));//Recuperando o arquivo
            if (file.exists()) {
                //Montando Uri de upload do arquivo
                Uri fileUrl = Uri.fromFile(file);
                String referencia = extras.get("referencia") + "/" + extras.get("usuario") + "/";
                StorageReference fileReference = storageReference.child(referencia + fileUrl.getLastPathSegment());

                //Iniciando Upload para o firebase
                UploadTask uploadTask = fileReference.putFile(fileUrl);
                uploadTask.addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        createNotification(startId, STATUS_ERRO);//Notifica de erro
                        servicesRunanble(startId, false);//Tarefa finalizada.
                        atualizaBase((String) extras.get("identificador"), "falhou", "");//Atualiza database
                        e.printStackTrace();
                    }
                }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        createNotification(startId, STATUS_SUCESS);//Notifica sucesso
                        servicesRunanble(startId, false);//Tarefa finalizada.
                        atualizaBase((String) extras.get("identificador"), "enviado", taskSnapshot.getDownloadUrl().toString());//Atualiza database
                    }
                });
            }
        }
    }

    //Atualizando registro no banco de dados
    private void atualizaBase(final String id, final String status, final String downloadUrl) {
        databaseReference.child(id).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                ChatFirebaseDomain chatFirebaseDomain = dataSnapshot.getValue(ChatFirebaseDomain.class);
                if (chatFirebaseDomain != null) {
                    if (!downloadUrl.equals("")) {
                        chatFirebaseDomain.setUrl(downloadUrl);
                    }
                    chatFirebaseDomain.setStatus(status);
                    database = FirebaseDatabase.getInstance();
                    databaseReference.child(dataSnapshot.getKey()).setValue(chatFirebaseDomain);
                }
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }

    //Metodo responsavel por adicionar as notificações
    private void servicesRunanble(int startId, boolean status) {
        notifyIds.put(startId, status);
    }

    //Criando as notificações de status
    public void createNotification(int id, int status) {
        String titulo = null;
        String mensagem = null;
        int icone = 0;
        boolean progress = false;

        switch (status) {
            case STATUS_START:
                titulo = "Envio em progresso...";
                mensagem = "Enviando arquivo...";
                icone = R.drawable.ic_cloud_send;
                progress = true;
                break;
            case STATUS_STOP:
                titulo = "Envio falhou...";
                mensagem = "O aplicativo foi encerrado!";
                icone = R.drawable.ic_cloud_off;
                break;
            case STATUS_ERRO:
                titulo = "Envio falhou...";
                mensagem = "O arquivo não foi enviando!";
                icone = R.drawable.ic_cloud_off;
                break;
            case STATUS_SUCESS:
                titulo = "Envio concluido...";
                mensagem = "Arquivo enviando com sucesso!";
                icone = R.drawable.ic_cloud_done;
                break;
        }

        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pIntent = PendingIntent.getActivity(this, (int) System.currentTimeMillis(), intent, 0);

        NotificationManager mNotifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this);
        mBuilder.setColor(Color.parseColor("#333333"));
        mBuilder.setContentTitle(titulo);
        mBuilder.setContentText(mensagem);
        mBuilder.setSmallIcon(icone);
        if (progress)
            mBuilder.setProgress(0, 0, true);
        else
            mBuilder.setAutoCancel(true);
        mNotifyManager.notify(id, mBuilder.build());
    }
}
