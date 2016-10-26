package com.tower;

import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;


public class MainActivity extends Activity {
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(new MyView(this));
        
    }

    
    //能显示出手写轨迹的view
    public class MyView extends SurfaceView implements Callback, Runnable{

        //按下返回键即退出程序
        @Override
        public boolean onKeyDown(int keyCode, KeyEvent event) {
            
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                finish();
            }

            return true;
        }

        //建立手写输入对象
        long recognizer = 0;
        long character = 0;
        long result = 0;
        int modelState = 0;  //显示model文件载入状态
        int strokes = 0; //总笔画数
        
        boolean resultDisplay = false; //是否显示结果
        
        int handwriteCount = 0; //笔画数
        
        private Thread mThread;
        SurfaceHolder mSurfaceHolder = null;
        Canvas mCanvas = null;
        Paint mPaint = null;
        Path mPath = null;
        Paint mTextPaint = null; //文字画笔
        public static final int FRAME = 60;//画布更新帧数
        boolean mIsRunning = false; //控制是否更新
        float posX, posY; //触摸点当前座标
        //触发定时识别任务
        Timer tExit;
        TimerTask task;
        
        public MyView(Context context) {
            super(context);

            //设置拥有焦点
            this.setFocusable(true);
            //设置触摸时拥有焦点
            this.setFocusableInTouchMode(true);
            //获取holder
            mSurfaceHolder = this.getHolder();
            //添加holder到callback函数之中
            mSurfaceHolder.addCallback(this);
            
            //创建画布
            mCanvas = new Canvas();
            
            //创建画笔
            mPaint = new Paint();
            mPaint.setColor(Color.BLUE);//颜色
            mPaint.setAntiAlias(true);//抗锯齿
            //Paint.Style.STROKE 、Paint.Style.FILL、Paint.Style.FILL_AND_STROKE 
            //意思分别为 空心 、实心、实心与空心 
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeCap(Paint.Cap.ROUND);//设置画笔为圆滑状
            mPaint.setStrokeWidth(5);//设置线的宽度
            
            //创建路径轨迹
            mPath = new Path();
            
            //创建文字画笔
            mTextPaint = new Paint();
            mTextPaint.setColor(Color.BLACK);
            mTextPaint.setTextSize(15);
            
            //创建手写识别
            if (character == 0) {
                character = characterNew();
                characterClear(character);
                characterSetWidth(character, 300);
                characterSetHeight(character, 300);
            }
            if (recognizer == 0) {
                recognizer = recognizerNew();
            }
            
            //打开成功返回1
            modelState = recognizerOpen(recognizer, "/sdcard/handwriting-zh_CN.model");
            if (modelState != 1) {
                System.out.println("model文件打开失败");
                return;
            }
        }
        
        @Override
        public boolean onTouchEvent(MotionEvent event) {

            //获取触摸动作以及座标
            int action = event.getAction();
            float x = event.getX();
            float y = event.getY();
            
            //按触摸动作分发执行内容
            switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (tExit != null) {
                    tExit.cancel();
                    tExit = null;
                    task = null;
                }
                resultDisplay = false;
                mPath.moveTo(x, y);//设定轨迹的起始点
                break;

            case MotionEvent.ACTION_MOVE:
                mPath.quadTo(posX, posY, x, y); //随触摸移动设置轨迹
                characterAdd(character, handwriteCount, (int)x, (int)y);
                break;
                
            case MotionEvent.ACTION_UP:
                handwriteCount++;
                tExit = new Timer();
                task = new TimerTask() {
                    
                    @Override
                    public void run() {
                        resultDisplay = true;
                    }
                };
                tExit.schedule(task, 1000);
                break;
            }
            
            //记录当前座标
            posX = x;
            posY = y;
            
            return true;
        }


        private void Draw(){
            //防止canvas为null导致出现null pointer问题
            if (mCanvas != null) {
                mCanvas.drawColor(Color.WHITE);  //清空画布
                mCanvas.drawPath(mPath, mPaint);    //画出轨迹
                //数据记录
                mCanvas.drawText("model打开状态 : " + modelState, 5, 20, mTextPaint);
                mCanvas.drawText("触点X的座标 : " + posX, 5, 40, mTextPaint);
                mCanvas.drawText("触点Y的座标 : " + posY, 5, 60, mTextPaint);
                
                strokes = (int)characterStrokesSize(character);
                mCanvas.drawText("总笔画数 : " + strokes, 5, 80, mTextPaint);
            }
            
            //进行文字检索
            if (strokes > 0 && resultDisplay) {
                result = recognizerClassify(recognizer, character, 10); 
                if (tExit != null) {
                    tExit.cancel();
                    tExit = null;
                    task = null;
                }
                characterClear(character);
                strokes = 0;
                mPath.reset();//触摸结束即清除轨迹
                resultDisplay = false;
                handwriteCount = 0;
            }
            
            //显示识别出的文字
            if (result != 0) {
                for (int i = 0; i < resultSize(result); i++) {
                    mCanvas.drawText(resultValue(result, i) + " : " + resultScore(result, i), 5, 100 + i * 20, mTextPaint);
                }    
            }

        }
        

        
        @Override
        public void run() {

            while(mIsRunning){
                //更新前的时间
                long startTime = System.currentTimeMillis();
                
                //线程安全锁
                synchronized(mSurfaceHolder){
                    mCanvas = mSurfaceHolder.lockCanvas();
                    Draw();
                    mSurfaceHolder.unlockCanvasAndPost(mCanvas);
                }
                //获取更新后的时间
                long endTime = System.currentTimeMillis();
                //获取更新时间差
                int diffTime = (int)(endTime - startTime);
                //确保每次更新都为FRAME
                while(diffTime <= FRAME){
                    diffTime = (int)(System.currentTimeMillis() - startTime);
                    //Thread.yield(): 与Thread.sleep(long millis):的区别，
                    //Thread.yield(): 是暂停当前正在执行的线程对象 ，并去执行其他线程。
                    //Thread.sleep(long millis):则是使当前线程暂停参数中所指定的毫秒数然后在继续执行线程
                    Thread.yield();
                }
            }
            
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            mIsRunning = true;
            mThread = new Thread(this);
            mThread.start();
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width,
                int height) {
            // TODO Auto-generated method stub
            
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            resultDestroy(result);
            characterDestroy(character);
            recognizerDestroy(recognizer);
            mThread = null;
        }
        
    }
    
    
    
    
    
    
    
    //jni封装方法的声明
    //charater
    public native long characterNew();
    public native void characterDestroy(long c);
    public native void characterClear(long stroke);
    public native int characterAdd(long character, long id, int x, int y);
    public native void characterSetWidth(long character, long width);
    public native void characterSetHeight(long character, long height);
    
    public native long characterStrokesSize(long character);
    
    //recognizer
    public native long recognizerNew();
    public native void recognizerDestroy(long recognizer);
    public native int recognizerOpen(long recognizer, String filename);
    public native String recognizerStrerror(long recognizer);
    public native long recognizerClassify(long recognizer, long character, long nbest);
    
    //result
    public native String resultValue(long result, long index);
    public native float resultScore(long result, long index);
    public native long resultSize(long result);
    public native void resultDestroy(long result);
    
    //载入.so文件
    static{
        System.loadLibrary("zinniajni");
    }
    
}