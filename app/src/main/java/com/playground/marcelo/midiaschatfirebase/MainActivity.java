package com.playground.marcelo.midiaschatfirebase;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.databinding.DataBindingUtil;
import android.media.MediaRecorder;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.playground.marcelo.midiaschatfirebase.databinding.AudioBinding;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class MainActivity extends AppCompatActivity {

    private AudioBinding binding;
    private String userUid = null;
    private String mensagemKey = null;
    private Intent storageService;

    //Referencias de serviços Firebase
    private FirebaseAuth auth;
    private FirebaseDatabase database;
    private DatabaseReference databaseReference;

    //Trabalhando com arquivos
    private String selectedImagePath;

    //Especificacoes de midias
    private String midiaPath;
    private String midiaName;
    private File file;
    private Uri outputFileUri;

    private final int TYPE_PHOTO = 1;
    private final int TYPE_VIDEO = 2;
    private final int TYPE_FILE = 3;
    private final int TYPE_AUDIO = 4;

    private MediaRecorder mRecorder = null;
    private Vibrator vibrator;
    private long inicioRecorder;

    private boolean altorizacao = false;
    String[] permissoes = new String[]{
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA,
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);

        //Referencias de servições do Firebase
        auth = FirebaseAuth.getInstance();
        //Pedindo permissoes ao usuario
        altorizacao = PermissionUtils.validate(this, 0, permissoes);
        //Inicializando servico vibrator
        vibrator = (Vibrator) this.getSystemService(VIBRATOR_SERVICE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        auth.signInAnonymously().addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
                if (task.isSuccessful()) { //Login efetuado com sucesso
                    FirebaseUser usuario = auth.getCurrentUser();
                    if (usuario != null) {
                        userUid = usuario.getUid();
                        manipulaChatFirebaseDomain();
                    }
                }
            }
        });
    }

    //Verifica se usuário tem conexão com internet
    private boolean checkInternet() {
        if (!isOnline()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle("Ooops");
            builder.setMessage("É necessário ter conexão com internet.");
            AlertDialog dialog = builder.create();
            dialog.show();
            return false;
        } else {
            return true;
        }
    }

    //Verficia se foi dando permissões necessárias
    private boolean checkPermission() {
        System.out.println("LOG: Permissão CHECK " + altorizacao);
        //Se não tiver permissão parar execucão
        if (!altorizacao) {
            System.out.println("LOG: Entrou!");

            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle("Ooops");
            builder.setMessage("Você não deu as permissoes requeridas!");
            AlertDialog dialog = builder.create();
            dialog.show();

            return false;
        } else {
            return true;
        }
    }

    private void manipulaChatFirebaseDomain() {
        //Selecionando arquivo da galeria
        binding.arquivoId.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkInternet()) {
                    Intent intent = new Intent();
                    intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    intent.setType("image/* video/*");
                    startActivityForResult(intent, TYPE_FILE);
                }
            }
        });

        //Pegando video da camera
        binding.videoId.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkPermission() && checkInternet()) {
                    Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, prepareMidia(TYPE_VIDEO));
                    intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
                    startActivityForResult(intent, TYPE_VIDEO);
                }
            }
        });

        //Pegando foto da camera
        binding.fotoId.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkPermission() && checkInternet()) {
                    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, prepareMidia(TYPE_PHOTO));
                    startActivityForResult(intent, TYPE_PHOTO);
                }
            }
        });

        //Interação para gravar audio
        binding.audioId.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (checkPermission() && checkInternet()) {
                            //Setando diretorio do arquivo
                            prepareMidia(TYPE_AUDIO).toString();
                            //Parametros de configuração do audio
                            mRecorder = new MediaRecorder();
                            mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                            mRecorder.setOutputFile(midiaPath + midiaName);
                            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

                            try {
                                //Inicia a gravação do audio
                                mRecorder.prepare();
                                vibrator.vibrate(80);
                                Calendar c = Calendar.getInstance();
                                inicioRecorder = c.getTimeInMillis();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            mRecorder.start();
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        try {
                            //Finaliza a gravação
                            mRecorder.stop();
                            mRecorder.release();
                            vibrator.vibrate(80);

                            Calendar c = Calendar.getInstance();
                            if (c.getTimeInMillis() - inicioRecorder < 500 || !checkInternet())
                                apagaArquivo(midiaPath + midiaName);//Apagar audio se menor que 1/2 segundo
                            else
                                sendMidia(midiaPath + midiaName, "audio");
                        } catch (RuntimeException e) {
                            apagaArquivo(midiaPath + midiaName);//Apaga audio se der erro
                            e.printStackTrace();
                        }
                        mRecorder = null;//Libera o recorder para receber um novo audio
                        break;
                }
                return false;
            }
        });
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        System.out.println("LOG: mostrando " + requestCode);

        if (resultCode == RESULT_OK) {
            Uri selectedMidiaUri;
            switch (requestCode) {
                case TYPE_FILE:
                    selectedMidiaUri = data.getData();
                    selectedImagePath = getPath(selectedMidiaUri);
                    sendMidia(selectedImagePath, "midia");
                    break;
                case TYPE_VIDEO:
                    selectedImagePath = getPath(outputFileUri);
                    sendMidia(selectedImagePath, "video");
                    break;
                case TYPE_PHOTO:
                    selectedImagePath = getPath(outputFileUri);
                    sendMidia(selectedImagePath, "image");
                    break;
                default:
                    System.out.println("LOG: " + requestCode);
            }
        }
    }

    //Pega diretorio completo de um determinado arquivo
    public String getPath(Uri uri) {
        if (uri == null) {
            return null;
        }
        String[] projection = {MediaStore.Images.Media.DATA};
        Cursor cursor = managedQuery(uri, projection, null, null, null);
        if (cursor != null) {
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(column_index);
        }
        return uri.getPath();
    }

    //Retorna a data atual já no padrão
    private String dataAtual() {
        Calendar c = Calendar.getInstance();
        SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyy HH:mm:ss");
        return df.format(c.getTime());
    }

    //Retorna a data atual no formato para salvar arquivos
    private String fileData() {
        Calendar c = Calendar.getInstance();
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
        return df.format(c.getTime());
    }

    //Cria um diretorio
    private void criaDiretorio(String diretorio) {
        File file = new File(diretorio);
        if (!file.isDirectory()) file.mkdirs();
        System.out.println("LOG: " + diretorio);
    }

    //Apaga arquivo em determinada pasta
    private void apagaArquivo(String arquivo) {
        if (arquivo != null) {
            File file = new File(arquivo);
            if (file.exists()) file.delete();
        }
    }

    //Preparando arquivo e diretorio para salvar o arquivo
    private Uri prepareMidia(int type) {
        //Diretorio padrão
        midiaPath = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "MidiasRadio";
        midiaName = null;
        switch (type) {
            case TYPE_VIDEO:
                midiaPath += File.separator + "video" + File.separator;
                midiaName = "VID_" + fileData() + ".mp4";
                criaDiretorio(midiaPath);
                file = new File(midiaPath + midiaName);
                break;
            case TYPE_PHOTO:
                midiaPath += File.separator + "photo" + File.separator;
                midiaName = "PHT_" + fileData() + ".jpg";
                criaDiretorio(midiaPath);
                file = new File(midiaPath + midiaName);
                break;
            case TYPE_AUDIO:
                midiaPath += File.separator + "audio" + File.separator;
                midiaName = "AUD_" + fileData() + ".mp3";
                criaDiretorio(midiaPath);
                file = new File(midiaPath + midiaName);
                break;
            default:
                return null;
        }
        outputFileUri = Uri.fromFile(file);
        return outputFileUri;
    }

    private void sendMidia(String arquivo, String tipo) {
        if (arquivo != null) {
            ChatFirebaseDomain chatFirebaseDomain = new ChatFirebaseDomain();
            chatFirebaseDomain.setTipo(tipo);
            chatFirebaseDomain.setDestino("root");
            chatFirebaseDomain.setStatus("aguardando");
            chatFirebaseDomain.setData(dataAtual());
            chatFirebaseDomain.setArquivo(arquivo);
            chatFirebaseDomain.setOrigem(userUid);
            chatFirebaseDomain.setNome("");
            chatFirebaseDomain.setFoto("");

            //Grava no database realtime
            database = FirebaseDatabase.getInstance();
            databaseReference = database.getReference("ChatFirebaseDomain");
            mensagemKey = databaseReference.push().getKey();
            databaseReference.child(mensagemKey).setValue(chatFirebaseDomain);

            storageService = new Intent(MainActivity.this, StorageService.class);
            storageService.putExtra("arquivo", arquivo);
            storageService.putExtra("identificador", mensagemKey);
            storageService.putExtra("usuario", userUid);
            storageService.putExtra("referencia", tipo);
            startService(storageService);
        }
    }

    //Retorna se usuário tem conexão
    public boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mRecorder != null) {
            try {
                mRecorder.stop();
                mRecorder.release();
                mRecorder = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (storageService != null) stopService(storageService);
        super.onDestroy();
    }

    //Interseptando alteração em permissões
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        for (int result : grantResults) {
            if (result == PackageManager.PERMISSION_DENIED) {
                // Alguma permissão foi negada
                altorizacao = false;
                return;
            }
        }
        //Permissoes OK
        altorizacao = true;
    }
}