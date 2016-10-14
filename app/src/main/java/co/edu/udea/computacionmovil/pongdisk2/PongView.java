package co.edu.udea.computacionmovil.pongdisk2;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Paint.Style;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import java.util.Random;

/**
 * Created by estudiantelis on 13/10/16.
 */

//vista de los atributos de dibujo del pongdisk, rebote, pelota, texto y dialogo

public class PongView extends View implements View.OnTouchListener, View.OnKeyListener {
    @SuppressWarnings("unused")
    private static final String TAG = "PongView";
    protected static final int FPS = 30;

    public static final int
            STARTING_LIVES = 1,
            PLAYER_PADDLE_SPEED = 10;


    private State mCurrentState = State.Running;
    private State mLastState = State.Stopped;
    public static enum State { Running, Stopped}

   //inicializador
    private boolean mInitialized = false;
    private int mBallSpeedModifier;
    private int mLivesModifier;
    private int mAiStrategy;
    private int mCpuHandicap;
    private boolean mNewRound = true;
    private boolean mContinue = true;
    private boolean mMuted = false;
    private Paddle mRed, mBlue;
    private Rect mPauseTouchBox;
    private long mLastFrame = 0;
    protected Ball mBall = new Ball();
    private static final Random RNG = new Random();//Genera un numero aleatorio para el disco
    protected SoundPool mPool = new SoundPool(3, AudioManager.STREAM_MUSIC, 0);
    protected int mWinSFX, mMissSFX, mPaddleSFX, mWallSFX;
    private final Paint mPaint = new Paint();
    private static final int PADDING = 3;
    private static final int SCROLL_SENSITIVITY = 80;
    private RefreshHandler mRedrawHandler = new RefreshHandler();
    private boolean mRedPlayer = false, mBluePlayer = false;

    /**
     * An overloaded class that repaints this view in a separate thread.
     * Calling PongView.update() should initiate the thread.
     * @author Grupo07
     *
     */
    class RefreshHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            PongView.this.update();
            PongView.this.invalidate();
        }

        public void sleep(long delay) {
            this.removeMessages(0);
            this.sendMessageDelayed(obtainMessage(0), delay);
        }
    }

    /**
     * Creates a new PongView within some context
     * @param context
     * @param attrs
     */
    public PongView(Context context, AttributeSet attrs) {
        super(context, attrs);
        constructView();
    }

    public PongView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        constructView();
    }

   //estado inicial de los botones y disco
    private void constructView() {
        setOnTouchListener(this);
        setOnKeyListener(this);
        setFocusable(true);

        Context ctx = this.getContext();
        loadPreferences( PreferenceManager.getDefaultSharedPreferences(ctx) );
        cargaSonido();
    }

    protected void cargaSonido() {
        Context ctx = getContext();
        mWinSFX = mPool.load(ctx, R.raw.wintone, 1);
        mMissSFX = mPool.load(ctx, R.raw.ballmiss, 1);
        mPaddleSFX = mPool.load(ctx, R.raw.paddle, 1);
        mWallSFX = mPool.load(ctx, R.raw.wall, 1);
    }

    protected void loadPreferences(SharedPreferences prefs) {
        Context ctx = getContext();
        Resources r = ctx.getResources();

        mBallSpeedModifier = Math.max(0, prefs.getInt(PongDisk.PREF_BALL_SPEED, 0));//modificación de la velocidad
        mMuted = prefs.getBoolean(PongDisk.PREF_MUTED, mMuted);//Sonido opciones
        mLivesModifier = Math.max(0, prefs.getInt(PongDisk.PREF_LIVES, 2));
        mCpuHandicap = Math.max(0, Math.min(PLAYER_PADDLE_SPEED-1, prefs.getInt(PongDisk.PREF_HANDICAP, 4)));

        String strategy = prefs.getString(PongDisk.PREF_STRATEGY, null);
        String strategies[] = r.getStringArray(R.array.values_ai_strategies);

        mAiStrategy = 0;
        for(int i = 0; strategy != null && strategy.length() > 0 && i < strategies.length; i++) {
            if(strategy.equals(strategies[i])) {
                mAiStrategy = i;
                break;
            }
        }
    }

    //carga la actualización de la vista
    public void update() {
        if(getHeight() == 0 || getWidth() == 0) {
            mRedrawHandler.sleep(1000 / FPS);
            return;
        }

        if(!mInitialized) {
            initializePongView();
            mInitialized = true;
        }

        long now = System.currentTimeMillis();
        if(gameRunning() && mCurrentState != State.Stopped) {
            if(now - mLastFrame >= 1000 / FPS) {
                if(mNewRound) {
                    nextRound();
                    mNewRound = false;
                }
                metodoJuego();
            }
        }

       //para mejoramiento de procesamiento
        if(mContinue) {
            long diff = System.currentTimeMillis() - now;
            mRedrawHandler.sleep(Math.max(0, (1000 / FPS) - diff) );
        }
    }

    //inicializa el estado maquina usuario
    private void metodoJuego() {
        float px = mBall.x;
        float py = mBall.y;

        mBall.move();
        if(py == mBall.y && mBall.serving() == false) {
            mBall.randomAngle();
        }
        if(!mRed.player) maquinaI(mRed, mBlue);
        else mRed.move();

        if(!mBlue.player) maquinaI(mBlue, mRed);
        else mBlue.move();

        tomaRebotes(px,py);
        if(mBall.y >= getHeight()) {
            mNewRound = true;
            mBlue.loseLife();

            if(mBlue.living()) playSound(mMissSFX);
            else playSound(mWinSFX);
        }
        else if (mBall.y <= 0) {
            mNewRound = true;
            mRed.loseLife();
            if(mRed.living()) playSound(mMissSFX);
            else playSound(mWinSFX);
        }
    }

    //rebote de la pelota se usa protegido para que este se pueda usar dentro del sistema
    protected void tomaRebotes(float px, float py) {
        velocidadRebote(mRed, px, py);
        reboteFondo(mBlue, px, py);
        if(mBall.x <= Ball.RADIUS || mBall.x >= getWidth() - Ball.RADIUS) {
            mBall.bounceWall();
            playSound(mWallSFX);
            if(mBall.x == Ball.RADIUS)
                mBall.x++;
            else
                mBall.x--;
        }

    }
    //se usa para la velocidad del rebote
    protected void velocidadRebote(Paddle paddle, float px, float py) {
        if(mBall.goingUp() == false) return;

        float tx = mBall.x;
        float ty = mBall.y - Ball.RADIUS;
        float ptx = px;
        float pty = py - Ball.RADIUS;
        float dyp = ty - paddle.getBottom();
        float xc = tx + (tx - ptx) * dyp / (ty - pty);

        if(ty < paddle.getBottom() && pty > paddle.getBottom()
                && xc > paddle.getLeft() && xc < paddle.getRight()) {

            mBall.x = xc;
            mBall.y = paddle.getBottom() + Ball.RADIUS;
            mBall.bouncePaddle(paddle);
            playSound(mPaddleSFX);
            increaseDifficulty();
        }
    }

    protected void reboteFondo(Paddle paddle, float px, float py) {
        if(mBall.goingDown() == false) return;

        float bx = mBall.x;
        float by = mBall.y + Ball.RADIUS;
        float pbx = px;
        float pby = py + Ball.RADIUS;
        float dyp = by - paddle.getTop();
        float xc = bx + (bx - pbx) * dyp / (pby - by);

        if(by > paddle.getTop() && pby < paddle.getTop()
                && xc > paddle.getLeft() && xc < paddle.getRight()) {

            mBall.x = xc;
            mBall.y = paddle.getTop() - Ball.RADIUS;
            mBall.bouncePaddle(paddle);
            playSound(mPaddleSFX);
            increaseDifficulty();
        }
    }

    private void maquinaI(Paddle cpu, Paddle opponent) {
        switch(mAiStrategy) {
            case 2:	aiFollow(cpu); break;
            case 1:	aiExact(cpu); break;
            default: aiPrediction(cpu,opponent); break;
        }
    }

    /**
     * Este es oara la prediccion del rebote, se usa para cuando este en la posicion y, tambien podamos observar la posicion X.
     * @param cpu
     */
    //se parametrizan todos los lados del choque
    private void aiPrediction(Paddle cpu, Paddle opponent) {
        Ball ball = new Ball(mBall);
        if(mBall.serving()) {//si la bola pasa por el centro
            cpu.destination = getWidth() / 2;
            cpu.move(true);
            return;
        }
        if(ball.vy == 0) return;
        //Son para saber las posiciones de rebote de los choques con los jugadores
        //las paletas
        float cpuDist = Math.abs(ball.y - cpu.centerY());
        float oppDist = Math.abs( ball.y - opponent.centerY() );
        float paddleDistance = Math.abs(cpu.centerY() - opponent.centerY());
        boolean coming = (cpu.centerY() < ball.y && ball.vy < 0)
                || (cpu.centerY() > ball.y && ball.vy > 0);
        float total = ((((coming) ? cpuDist : oppDist + paddleDistance)) / Math.abs(ball.vy)) * Math.abs( ball.vx );
        float playWidth = getWidth() - 2 * Ball.RADIUS;
        float wallDist = (ball.goingLeft()) ? ball.x - Ball.RADIUS : playWidth - ball.x + Ball.RADIUS;
        float remains = (total - wallDist) % playWidth;
        int bounces = (int) ((total) / playWidth);
        boolean left = (bounces % 2 == 0) ? !ball.goingLeft() : ball.goingLeft();
        cpu.destination = getWidth() / 2;
        //
        if(bounces == 0) {
            cpu.destination = (int) (ball.x + total * Math.signum(ball.vx));
        }
        else if(left) {
            cpu.destination = (int) (Ball.RADIUS + remains);
        }
        else {
            cpu.destination = (int) ((Ball.RADIUS + playWidth) - remains);
        }
        int salt = (int) (System.currentTimeMillis() / 10000);
        Random r = new Random((long) (cpu.centerY() + ball.vx + ball.vy + salt));
        int width = cpu.getWidth();
        cpu.destination = (int) bound(
                cpu.destination + r.nextInt(2 * width - (width / 5)) - width + (width / 10),
                0, getWidth()
        );
        cpu.move(true);
    }

    private void aiExact(Paddle cpu) {
        cpu.destination = (int) mBall.x;
        cpu.setPosition(cpu.destination);
    }

    private void aiFollow(Paddle cpu) {
        cpu.destination = (int) mBall.x;
        cpu.move(true);
    }

    private void increaseDifficulty() {
        mBall.speed++;
    }

    /**
     * Cuando inicia en el siguiente, es diferente al anterior
     * @param next, siguiente fase
     */
    public void setMode(State next) {
        mCurrentState = next;
        nextRound();
        update();
    }

    private void nextRound() {
        serveBall();
    }//siguiente ronda vuelve cero los parametros

    private void initializePongView() {
        initializePause();
        initializePaddles();
    }

    private void initializePause() {
        int min = Math.min(getWidth() / 4, getHeight() / 4);
        int xmid = getWidth() / 2;
        int ymid = getHeight() / 2;
        mPauseTouchBox = new Rect(xmid - min, ymid - min, xmid + min, ymid + min);
    }

    private void initializePaddles() {
        Rect redTouch = new Rect(0,0,getWidth(),getHeight() / 8);
        Rect blueTouch = new Rect(0, 7 * getHeight() / 8, getWidth(), getHeight());

        mRed = new Paddle(Color.RED, redTouch.bottom + PADDING);
        mBlue = new Paddle(Color.BLUE, blueTouch.top - PADDING - Paddle.PADDLE_THICKNESS);

        mRed.setTouchbox( redTouch );
        mBlue.setTouchbox( blueTouch );

        mRed.setHandicap(mCpuHandicap);
        mBlue.setHandicap(mCpuHandicap);

        mRed.player = mRedPlayer;
        mBlue.player = mBluePlayer;

        mRed.setLives(STARTING_LIVES + mLivesModifier);
        mBlue.setLives(STARTING_LIVES + mLivesModifier);
    }

    //la bola al estado inicial
    private void serveBall() {
        mBall.x = getWidth() / 2;
        mBall.y = getHeight() / 2;
        mBall.speed = Ball.SPEED + mBallSpeedModifier;
        mBall.randomAngle();
        mBall.pause();
    }

    protected float bound(float x, float low, float hi) {
        return Math.max(low, Math.min(x, hi));
    }

    /**
     * Usa backtraking para la posicion siguiente del rebote
     * @author grupo07
     *
     */
    class Point {
        private int x, y;
        Point() {
            x = 0; y = 0;
        }

        Point(int x, int y) {
            this.x = x; this.y = y;
        }

        public int getX() { return x; }
        public int getY() { return y ; }
        public void set(double d, double e) { this.x = (int) d; this.y = (int) e; }

        public void translate(int i, int j) { this.x += i; this.y += j; }

        @Override
        public String toString() {
            return "Point: (" + x + ", " + y + ")";
        }
    }

    public void onSizeChanged(int w, int h, int ow, int oh) {
    }

    //pintar el juego, aqui se usa para ver los colores y demas del juego
    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if(mInitialized == false) {//si estado inicial es falso ya que aun no se pone nada en pantalla
            return;
        }
        Context context = getContext();//se usa para saber el estado de los parametros
                                        //para saber en que estado esta o que esta sucediendo en el momento
        mRed.draw(canvas);//Son los lienzos de los jugadores
        mBlue.draw(canvas);

        if(gameRunning() && mRed.player && mCurrentState == State.Running)
            mRed.drawTouchbox(canvas);

        if(gameRunning() && mBlue.player && mCurrentState == State.Running)
            mBlue.drawTouchbox(canvas);

        // Dibujar objeto (disco)
        mPaint.setStyle(Style.FILL);
        mPaint.setColor(Color.BLACK);

        mBall.draw(canvas);

       //si no estan jugando esto les hace dar a entender que no juegan pero se pueden unir.
        if(mBall.serving()) {
            String join = context.getString(R.string.join_in);
            int joinw = (int) mPaint.measureText(join);

            if(!mRed.player) {
                mPaint.setColor(Color.RED);//color jugador y posicion de pantalla
                canvas.drawText(join, getWidth() / 2 - joinw / 2, mRed.touchCenterY(), mPaint);
            }

            if(!mBlue.player) {
                mPaint.setColor(Color.BLUE);//color jugador y posicion de pantalla
                canvas.drawText(join, getWidth() / 2 - joinw / 2, mBlue.touchCenterY(), mPaint);
            }
        }

        // Enseña donde pueden tocar para jugar cuando el juego esta en pausa
        if(mBall.serving()) {
            String pause = context.getString(R.string.pause);
            int pausew = (int) mPaint.measureText(pause);

            mPaint.setColor(Color.GREEN);
            mPaint.setStyle(Style.STROKE);
            canvas.drawRect(mPauseTouchBox, mPaint);
            canvas.drawText(pause, getWidth() / 2 - pausew / 2, getHeight() / 2, mPaint);
        }

        // Mensaje de pausa
        if(gameRunning() && mCurrentState == State.Stopped) {
            String s = context.getString(R.string.paused);
            int width = (int) mPaint.measureText(s);
            int height = (int) (mPaint.ascent() + mPaint.descent());
            mPaint.setColor(Color.BLACK);
            canvas.drawText(s, getWidth() / 2 - width / 2, getHeight() / 2 - height / 2, mPaint);
        }

        //Contador de vidas
        mPaint.setColor(Color.BLACK);
        mPaint.setStyle(Style.FILL_AND_STROKE);
        for(int i = 0; i < mRed.getLives(); i++) {
            canvas.drawCircle(Ball.RADIUS + PADDING + i * (2 * Ball.RADIUS + PADDING),
                    PADDING + Ball.RADIUS,
                    Ball.RADIUS,
                    mPaint);
        }

        for(int i = 0; i < mBlue.getLives(); i++) {
            canvas.drawCircle(Ball.RADIUS + PADDING + i * (2 * Ball.RADIUS + PADDING),
                    getHeight() - PADDING - Ball.RADIUS,
                    Ball.RADIUS,
                    mPaint);
        }

        // Anuncio de ganador
        if(!gameRunning()) {
            mPaint.setColor(Color.GREEN);
            String s = "You both lose";

            if(!mBlue.living()) {
                s = context.getString(R.string.red_wins);
                mPaint.setColor(Color.RED);
            }
            else if(!mRed.living()) {
                s = context.getString(R.string.blue_wins);
                mPaint.setColor(Color.BLUE);
            }

            int width = (int) mPaint.measureText(s);
            int height = (int) (mPaint.ascent() + mPaint.descent());
            canvas.drawText(s, getWidth() / 2 - width / 2, getHeight() / 2 - height / 2, mPaint);
        }
    }

    //mencionado anteriormente para jugar si quiere unirse, tocar pantalla
    public boolean onTouch(View v, MotionEvent mo) {
        if(v != this || !gameRunning()) return false;
        ToquePantalla handle = ToquePantalla.getInstance();//toque de pantalla

        for(int i = 0; i < handle.getTouchCount(mo); i++) {
            int tx = (int) handle.getX(mo, i);
            int ty = (int) handle.getY(mo, i);
            //calidad de toque de los jugadores ya que no todos los celulares soportan bien el doble toque
            if(mBlue.player && mBlue.inTouchbox(tx,ty)) {
                mBlue.destination = tx;
            }
            else if(mRed.player && mRed.inTouchbox(tx,ty)) {
                mRed.destination = tx;
            }
            else if(mo.getAction() == MotionEvent.ACTION_DOWN && mPauseTouchBox.contains(tx, ty)) {
                if(mCurrentState != State.Stopped) {
                    mLastState = mCurrentState;
                    mCurrentState = State.Stopped;
                }
                else {
                    mCurrentState = mLastState;
                    mLastState = State.Stopped;
                }
            }
            //si se quiere unir un jugador, simplemente tocar la pantalla, este evento funciona para hambos casos
            if(mo.getAction() == MotionEvent.ACTION_DOWN) {
                if(!mBlue.player && mBlue.inTouchbox(tx,ty)) {
                    mBlue.player = true;
                }
                else if(!mRed.player && mRed.inTouchbox(tx,ty)) {
                    mRed.player = true;
                }
            }
        }

        return true;
    }

    @Override
    public boolean onTrackballEvent(MotionEvent event) {
        if(!gameRunning()) return false;

        if(mBlue.player == false) {
            mBlue.player = true;
            mBlue.destination = mBlue.centerX();
        }

        switch(event.getAction()) {
            case MotionEvent.ACTION_MOVE:
                mBlue.destination = (int) Math.max(0, Math.min(getWidth(), mBlue.destination + SCROLL_SENSITIVITY * event.getX()));
                break;
        }

        return true;
    }
    //nuevo juego, nuevas vidas, inicializa de nuevo los parametros del juego
    public void newGame() {
        resetPaddles();
        serveBall();
        resumeLastState();
    }

    //reiniicar vidas
    private void resetPaddles() {
        int mid = getWidth() / 2;
        mRed.setPosition(mid);
        mBlue.setPosition(mid);
        mRed.destination = mid;
        mBlue.destination = mid;
        mRed.setLives(STARTING_LIVES);
        mBlue.setLives(STARTING_LIVES);
    }
    //cuando se pausa el juego, para seguir en el mismo
    private void resumeLastState() {
        if(mLastState == State.Stopped && mCurrentState == State.Stopped) {
            mCurrentState = State.Running;
        }
        else if(mCurrentState != State.Stopped) {
            // No tengo idea el por de que se necesite este, pero sin esta linea no me corre el programa jajajaja
            //mentiras, es un parametro para jugar despues de despausar el juego
        }
        else if(mLastState != State.Stopped) {
            mCurrentState = mLastState;
            mLastState = State.Stopped;
        }
    }

    public boolean gameRunning() {
        return mInitialized && mRed != null && mBlue != null
                && mRed.living() && mBlue.living();
    }

    public void pause() {
        mLastState = mCurrentState;
        mCurrentState = State.Stopped;
    }

    public boolean onKey(View v, int keyCode, KeyEvent event) {
        return false;
    }

    public void setPlayerControl(boolean red, boolean blue) {
        mRedPlayer = red;
        mBluePlayer = blue;
    }

    public void resume() {
        mContinue = true;
        update();
    }

    public void stop() {
        mContinue = false;
    }
    //activa lo que esta desactivado
    public void release() {
        mPool.release();
    }

    public void toggleMuted() {
        this.setMuted(!mMuted);
    }

    public void setMuted(boolean b) {
      //sonido
        mMuted = b;
        Context ctx = this.getContext();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(ctx);
        SharedPreferences.Editor editor = settings.edit();
        //grabar valor del sonido
        editor.putBoolean(PongDisk.PREF_MUTED, b);
        editor.commit();
        //toast para llamada de habilitación o desabilitación
        int rid = (mMuted) ? R.string.sound_disabled : R.string.sound_enabled;
        Toast.makeText(ctx, rid, Toast.LENGTH_SHORT).show();
    }
    //play al sonido
    private void playSound(int rid) {
        //##recordar cambiar para el sistema
        if(mMuted == false) return;
        mPool.play(rid, 0.2f, 0.2f, 1, 0, 1.0f);
    }

    class Ball {
        public float x, y, xp, yp, vx, vy;
        public float speed = SPEED;

        protected double mAngle;
        protected boolean mNextPointKnown = false;
        protected int mCounter = 0;

        public Ball() {
            findVector();
        }

        public Ball(Ball other) {
            x = other.x;
            y = other.y;
            xp = other.xp;
            yp = other.yp;
            vx = other.vx;
            vy = other.vy;
            speed = other.speed;
            mAngle = other.mAngle;
        }

        protected void findVector() {
            vx = (float) (speed * Math.cos(mAngle));
            vy = (float) (speed * Math.sin(mAngle));
        }

        public boolean goingUp() {
            return mAngle >= Math.PI;
        }

        public boolean goingDown() {
            return !goingUp();
        }

        public boolean goingLeft() {
            return mAngle <= 3 * Math.PI / 2 && mAngle > Math.PI / 2;
        }

        public boolean goingRight() {
            return !goingLeft();
        }

        public double getAngle() {
            return mAngle;
        }

        public boolean serving() {
            return mCounter > 0;
        }

        public void pause() {
            mCounter = 60;
        }

        public void move() {
            if(mCounter <= 0) {
                x = keepX(x + vx);
                y += vy;
            }
            else {
                mCounter--;
            }
        }

        public void randomAngle() {
            setAngle( Math.PI / 2 + RNG.nextInt(2) * Math.PI + Math.PI / 2 * RNG.nextGaussian() );
        }

        public void setAngle(double angle) {
            mAngle = angle % (2 * Math.PI);
            mAngle = boundAngle(mAngle);
            findVector();
        }

        public void draw(Canvas canvas) {
            if((mCounter / 10) % 2 == 1 || mCounter == 0)
                canvas.drawCircle(x, y, Ball.RADIUS, mPaint);
        }

        /**
         * Colisiones del disco
         * @param p,
         * @return retorna las colisiones si colisiona o no
         */
        public boolean collides(Paddle p) {
            return p.collides(this);
        }

       //verifiaciones matematicas para las colisiones
        public void bouncePaddle(Paddle p) {
            double angle;

            //colision derecha
            if(mAngle >= Math.PI) {
                angle = 4 * Math.PI - mAngle;
            }
            //colision izquierda
            else {
                angle = 2 * Math.PI - mAngle;
            }

            angle %= (2 * Math.PI);
            angle = salt(angle, p);
            setAngle(angle);
        }

       //rebote horizontal
        public void bounceWall() {
            setAngle(3 * Math.PI - mAngle);
        }

        protected double salt(double angle, Paddle paddle) {
            int cx = paddle.centerX();
            double halfWidth = paddle.getWidth() / 2;
            double change = 0.0;

            if(goingUp()) change = SALT * ((cx - x) / halfWidth);
            else change = SALT * ((x - cx) / halfWidth);

            return boundAngle(angle, change);
        }

        /**
         * Cuando golpea el disco a un jugador, este vuelve a correr
         * es cuando se choca y toma otra dirección
         * @param p cuando golpea la bola
         */
        protected void normalize(Paddle p) {//cuando esta cerca de la paleta y su colision
            if(x < p.getLeft() || x > p.getRight()) {
                return;
            }
            if(y < p.getTop()) {
                y = Math.min(y, p.getTop() - Ball.RADIUS);
            }
            else if(y > p.getBottom()) {
                y = Math.max(y, p.getBottom() + Ball.RADIUS);
            }
        }

        /**
         *
         * @param angle angulo inicial
         * @param angleChange agregar parametros al angulo
         * @return rebota a un direfente angulo
         */
        protected double boundAngle(double angle, double angleChange) {
            return boundAngle(angle + angleChange, angle >= Math.PI);
        }

        protected double boundAngle(double angle) {
            return boundAngle(angle, angle >= Math.PI);
        }

        /**
         *
         * @param angle rebote del angulo
         * @param top si choca arriba o no
         * @return retorna el angulo de rebote
         */
        protected double boundAngle(double angle, boolean top) {
            if(top) {
                return Math.max(Math.PI + BOUND, Math.min(2 * Math.PI - BOUND, angle));
            }

            return Math.max(BOUND, Math.min(Math.PI - BOUND, angle));
        }


        /**
         * da las coordenadas de x para el rebote
         * @param x, coordenadas de x
         * @return
         */
        protected float keepX(float x) {
            return bound(x, Ball.RADIUS, getWidth() - Ball.RADIUS);
        }

        public static final double BOUND = Math.PI / 9;
        public static final float SPEED = 4.0f;
        public static final int RADIUS = 4;
        public static final double SALT = 4 * Math.PI / 9;
    }

    class Paddle {
        protected int mColor;
        protected Rect mRect;
        protected Rect mTouch;
        protected int mHandicap = 0;
        protected int mSpeed = PLAYER_PADDLE_SPEED;
        protected int mLives = STARTING_LIVES;

        public boolean player = false;

        public int destination;

        public Paddle(int c, int y) {
            mColor = c;

            int mid = PongView.this.getWidth() / 2;
            mRect = new Rect(mid - PADDLE_WIDTH, y,
                    mid + PADDLE_WIDTH, y + PADDLE_THICKNESS);
            destination = mid;
        }

        public void move() {
            move(mSpeed);
        }

        public void move(boolean handicapped) {
            move((handicapped) ? mSpeed - mHandicap : mSpeed);
        }

        public void move(int s) {
            int dx = (int) Math.abs(mRect.centerX() - destination);

            if(destination < mRect.centerX()) {
                mRect.offset( (dx > s) ? -s : -dx, 0);
            }
            else if(destination > mRect.centerX()) {
                mRect.offset( (dx > s) ? s : dx, 0);
            }
        }

        public void setLives(int lives) {
            mLives = Math.max(0, lives);
        }

        public void setPosition(int x) {
            mRect.offset(x - mRect.centerX(), 0);
        }

        public void setTouchbox(Rect r) {
            mTouch = r;
        }

        public void setSpeed(int s) {
            mSpeed = (s > 0) ? s : mSpeed;
        }

        public void setHandicap(int h) {
            mHandicap = (h >= 0 && h < mSpeed) ? h : mHandicap;
        }

        public boolean inTouchbox(int x, int y) {
            return mTouch.contains(x, y);
        }

        public void loseLife() {
            mLives = Math.max(0, mLives - 1);
        }

        public boolean living() {
            return mLives > 0;
        }

        public int getWidth() {
            return Paddle.PADDLE_WIDTH;
        }

        public int getTop() {
            return mRect.top;
        }

        public int getBottom() {
            return mRect.bottom;
        }

        public int centerX() {
            return mRect.centerX();
        }

        public int centerY() {
            return mRect.centerY();
        }

        public int getLeft() {
            return mRect.left;
        }

        public int getRight() {
            return mRect.right;
        }

        public int touchCenterY() {
            return mTouch.centerY();
        }

        public int getLives() {
            return mLives;
        }

        public void draw(Canvas canvas) {
            mPaint.setColor(mColor);
            mPaint.setStyle(Style.FILL);
            canvas.drawRect(mRect, mPaint);
        }

        public void drawTouchbox(Canvas canvas) {
            mPaint.setColor(mColor);
            mPaint.setStyle(Style.STROKE);
            int mid = getHeight() / 2;
            int top = Math.abs(mTouch.top - mid), bot = Math.abs(mTouch.bottom - mid);
            float y = (top < bot) ? mTouch.top : mTouch.bottom;
            canvas.drawLine(mTouch.left, y, mTouch.right, y, mPaint);
        }

        public boolean collides(Ball b) {
            return b.x >= mRect.left && b.x <= mRect.right &&
                    b.y >= mRect.top - Ball.RADIUS && b.y <= mRect.bottom + Ball.RADIUS;
        }

        //grosor del palo
        private static final int PADDLE_THICKNESS = 20;

        //ancho del palo
        private static final int PADDLE_WIDTH = 60;
    }
}