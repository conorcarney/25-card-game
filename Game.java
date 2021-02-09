import java.util.Random;
import java.util.LinkedList;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.MouseEvent;
import java.awt.image.BufferStrategy;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;


public final class Game extends Canvas implements Runnable {

    public static void main(String[] args) {
        initGame();
    }
    
    private final int WINDOW_WIDTH = 800, WINDOW_HEIGHT = 600;
    private final GameWindow window; // window frame of the game
    private final GameHelper helper; // game logic handler
    private final Thread gameThread; // game will run on this thread
    private boolean running;
    
    Game() {
        window = new GameWindow(WINDOW_WIDTH, WINDOW_HEIGHT, "Never Give Up", (Game)this);
        helper = new GameHelper(window);
        gameThread = new Thread(this);
        // adding custom mouse control defined for the Player
        addMouseMotionListener(helper.getPlayer().getControl());
    }
    
    private static void initGame() {
        // starting the game on a new thread
        new Game().start();
    }
    
    private synchronized void start() {
        if (running) return;
        gameThread.start();
        running = true;
    }
    
    private synchronized void stop() {
        if (!running) return;
        try { gameThread.join(); } 
        catch(InterruptedException e) { }
        running = false;
    }
    
    // renders the background of the game world
    void renderBackGround(Graphics gfx) {
        gfx.setColor(Color.MAGENTA);
        gfx.fillRect(0, 0, WINDOW_WIDTH * 2, WINDOW_HEIGHT * 2);
    }

    // render all game graphics on the screen
    void render() {
        BufferStrategy bfs = this.getBufferStrategy();
        // this null checking is important because when the game
        // start, bfs will initially be null
        if (bfs == null) {
            // the number of screen buffer
            final int buffers = 0x2;
            // creating the buffer
            this.createBufferStrategy(buffers);
            return;
        }
        
        Graphics gfx = bfs.getDrawGraphics();
        renderBackGround(gfx); // render the background of the game window
        helper.renderAll(gfx); // render everything else on the screen

        bfs.show();
        gfx.dispose();
    }

    // update all entities of the game
    void update() {
        helper.updateAll();
    }  

    
    /*
        this is the frame rate optimzing algorithm
        it provide a constant 60 fps (given the computer can handle it)
    */
    
    @Override
    public void run() {
        long lastTime = System.nanoTime(); // current system time in nano secs
        double amountOfTicks = 60.0; // number of FPS
        double ns = 1000000000 / amountOfTicks;
        double delta = 0;
        while(running){
            long now = System.nanoTime(); // get system time in nano seconds
            // finds the time difference between lastTime and now, then divide
            // it by ns. this is value is then added to delta
            delta += (now - lastTime) / ns;
            lastTime = now;
            while(delta >= 1){
                update(); // update the game contents
                delta--;
            }
            render(); // render game contents
        }
    }
}


class GameHelper {
    
    private final GameWindow window;    
    private final Player player;
    // circuler queue to store Blocks
    private final BlockQueue mesh; 
    private final int width, 
            height, 
            blockWidth,
            pairs, 
            minBlockHeight, 
            passingGap;
    private int score = 0, lives = 3;
    
    public GameHelper(GameWindow window) {
        this.window = window;
        this.window.updateScore(score); // updates the score label
        this.window.updateLives(lives); // updates the lives label
        width = window.getSize().width;
        height = window.getSize().height;
        blockWidth = 60;
        pairs = 6;
        minBlockHeight = 100;
        passingGap = 100;
        player = new Player(width/2, height/2);
        mesh = new BlockQueue(pairs * 2);
        player.setBounds(width, height); // boundry of the player (ball)
        
        // creating Block when the game starts
        for (int i = 0; i < pairs; i++) {
            Block top, bottom; // two blocks of a pair
            
            // the desired X coordinate of the block
            int x = width + i * (width + blockWidth)/pairs;
            // height of the top block
            int topHeight = new Random().nextInt(height/2) + minBlockHeight;
            // the first two are arguments are the position and the other two
            // are size of the block
            top = new Block(x, -Block.OFF_SET, blockWidth, topHeight);
            
            // relative Y coordinate of the second block of the pair 
            int y = top.getSize().height - Block.OFF_SET + passingGap;
            int bottomHeight = height + Block.OFF_SET - y;
            bottom = new Block(x, y, blockWidth, bottomHeight);
            
            // both blocks are enqueued 
            mesh.enqueue(top);
            mesh.enqueue(bottom);
        }
    }
    // check whether the player is hitting any Blocks on the screen
    public boolean collision() {
        LinkedList<Block> blocksMesh = mesh.select();
        for (int i = 0; i < blocksMesh.size(); i += 2) {
            if (player.hit(blocksMesh.get(i)) || player.hit(blocksMesh.get(i+1))) {
                // returns true if the player hits a block
                return true;
            }
        }
        // otherwise false
        return false;
    }
    
    /*
        this method update block pairs and the score board
        it rellocates the pair of blocks that's about to go out of side on
        the left side of the screen
    */
    
    void updatePairs() {
        for (int i = 0; i < mesh.select().size(); i += 2) {
            Block b1 = mesh.select().get(i); // top block of the pair
            Block b2 = mesh.select().get(i+1); // bottom block of the pair
            if (b1 != null && b2 != null) {
                if (b1.position.x < -b1.getSize().width/2 || 
                        b2.position.x < -b2.getSize().width/2) {
                    rellocate(); // rellocates the blocks
                    // updates the score board (UI)
                    this.window.updateScore(++this.score);
                }
                // both Blocks are updated
                b1.update();
                b2.update();            
            }
        }
    }
    
    
    private void rellocate() {
        // removes the first two blocks from the queue
        mesh.dequeue();
        mesh.dequeue();
        
        // this is essentially what is written in the constructor
        // it creates the second wave of block pairs
        Block top, bottom;
        
        int x = mesh.getLast().getPosition().x + (width/pairs);
        int topHeight = new Random().nextInt(height/2) + minBlockHeight;
        top = new Block(x, -Block.OFF_SET, blockWidth, topHeight);
        
        int y = top.getSize().height - Block.OFF_SET + passingGap;
        int bottomHeight = height + Block.OFF_SET - y;
        bottom = new Block(x, y, blockWidth, bottomHeight);
        
        // adding the newly created blocks to the end of the mesh
        mesh.enqueue(top);
        mesh.enqueue(bottom);
    }
    
    public void renderAll(Graphics gfx) {
        // renders all the Blocks
        renderBlocks(gfx);
        // render the Player on the screen
        player.render(gfx);
    }    
    
    public void updateAll() {
        if (collision()) {
            lives--;
            // update the lives on the game screen
            window.updateLives(lives);
        }
        if (lives == 0) {
            // displays a prompt at the end of the game
            window.raiseMsg("GAMEOVER\nScore: " + String.valueOf(score));
        } else if (lives > 0) {
            // updating all the pair
            updatePairs();
            // updating the player
            player.update();
        }
    }
    
    void renderBlocks(Graphics gfx) {
        // iterate over all the blocks in the mesh (queue)
        // and renders them on the screen
        for (int i = 0; i < mesh.select().size(); i++) {
            Block selectedBlock = mesh.select().get(i);
            if (selectedBlock != null) // just to be on the safe side
                selectedBlock.render(gfx);
        }
    }
    
    public Player getPlayer() {
        return player;
    }
}

   





class GameWindow extends JFrame {
    
    // score board Labels
    static final class StatusBar {
        static JLabel score;
        static JLabel lives;
        static Font fonts;
    }
    
    public GameWindow(int width, int height, String title, Game game) {
        setSize(new Dimension(width, height)); // set the size of the window
        setTitle(title);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(true);
        setVisible(true);
        setCursor(new Cursor(Cursor.HAND_CURSOR)); // setting cursor to hand
        
        StatusBar.score = new JLabel();
        StatusBar.lives = new JLabel();  
        StatusBar.fonts = new Font("Serif", Font.BOLD, 18);
        
        // UI design of live label
        StatusBar.lives.setSize(80, 18);
        StatusBar.lives.setFont(StatusBar.fonts);
        StatusBar.lives.setForeground(Color.RED);
        StatusBar.lives.setLocation(width/2-StatusBar.lives.getSize().width, 0); 
        StatusBar.lives.setBorder(BorderFactory.createDashedBorder(Color.BLACK));

        // UI design of score label
        StatusBar.score.setSize(80, 18);
        StatusBar.score.setFont(StatusBar.fonts);
        StatusBar.score.setLocation(width/2, 0); 
        StatusBar.score.setForeground(Color.BLUE);
        StatusBar.score.setBorder(BorderFactory.createDashedBorder(Color.BLACK));
        
        // add both labels to the window
        add(StatusBar.lives);
        add(StatusBar.score);
        // added the game Object to the window
        add(game);
    }    

    public void updateLives(int lives) {
        StatusBar.lives.setText("Lives: " + lives);
    }
    
    public void updateScore(int score) {
        StatusBar.score.setText("Score: " + score);
    }
    
    // a util method for raise a pop up window
    // to show some useful information
    void raiseMsg(String msg) {
        if (JOptionPane.showConfirmDialog(this, msg, "Never Give Up", 
            JOptionPane.CLOSED_OPTION) == JOptionPane.OK_OPTION) System.exit(1);
    }
}




class BlockQueue { 
  
    private final int size; // max size of the queue
    private int front, rear; // front & rear of the queue
    private final LinkedList<Block> mesh; // list for storing block objects

    BlockQueue(int size) {
        mesh = new LinkedList<>();
        this.size = size; 
        this.front = -1;
        this.rear = -1; 
    } 

    public boolean enqueue(Block data) { // adding a block at the end of queue

        if((front == 0 && rear == size - 1) || // if the queue is full
                (rear == (front - 1) % (size - 1))) { 
            throw new Error("Queue is at full capacity");
        } else if(front == -1) { // first entry of the queue
            front = 0; 
            rear = 0; 
            mesh.add(rear, data); // stores data at index rear
        } else if(rear == size - 1 && front != 0) { 
            rear = 0; 
            mesh.set(rear, data);
        } else { 
            rear = (rear + 1); 
            if(front <= rear) { 
                mesh.add(rear, data); 
            } else { 
                mesh.set(rear, data); 
            } 
        } 
        // returns true only when the queue is not full, and not error occurs
        // during storing of blocks in the queue
        return true;
    } 

    public Block dequeue() { // removing the first block from the queue
        Block temp; // the object that will be returned from the method

        // don't wanna be dequeueing an empty queue
        if(front == -1)  { 
            System.out.print("Queue is Empty"); 
            return null;  
        } 

        temp = mesh.get(front);// stores the first Block in variable temp
        mesh.set(front, null); // set that position to null (empty)
        if(front == rear) { 
            front = -1; 
            rear = -1; 
        } else if(front == size - 1) { 
            front = 0; 
        } else { 
            front = front + 1; 
        } 
        return temp; 
    } 
    
    // returns the linked list. it is used for iteration purpose in
    // the GameHelper class
    public LinkedList<Block> select() {
        return this.mesh;
    }
    
    // returns the last Block of the queue
    public Block getLast() {
        return mesh.get(rear);
    }
}


class Block extends GameObject {
    
    private static int ID_REGISTER = 0; // unique id provider
    public static int OFF_SET = 30;
    private final int id;
    private boolean functional; // if false acts as a dummy block
  
    public Block(int x, int y, int w, int h) {
        super();
        this.id = ID_REGISTER++; // assign id and increments ID_REGISTER by 1
        size = new Size(w, h);
        velocity = new Velocity(4f, 4f);
        position = new Position(x, y);
        functional = true;
    }
    
    int getID() {
        return id;
    }
    
    boolean isFunctional() {
        return functional;
    }
    
    void setFunctional(boolean mode) {
        functional = mode;
    }
    
    @Override
    public void update() {
        // moving to the left
        position.x -= velocity.dx;
    }

    @Override
    public void render(Graphics gfx) {
        // if the id is an even number the block should be colored CYAN,
        // otherwise GREEN
        gfx.setColor((getID() % 2 == 0) ? Color.CYAN : Color.GREEN);
        // renders a rectangle at the given postion.
        gfx.fillRoundRect(
            position.x,
            position.y,
            size.width,
            size.height,
            60, // horizontal radius
            60 // vertical radius
        );
    }    
}


class Player extends GameObject {
    
    public Player(int x, int y) {
        super();
        position = new Position(x, y);
        size = new Size(24, 24);
        bounds = new Bounds(800, 600);
        // defining user controls for Player
        control = new Control() {
            // the overriden method will be called every time with mouse move
            @Override
            public void mouseMoved(MouseEvent event) {
                // sets ball position to mouse cursor position
                position.x = event.getX()-size.width/2;
                position.y = event.getY()-size.height/2;
            }
        };
    }
    
    
    /*
        this methods checks restricts the movement of the ball.
        the ball will stay inside the game window
    */
    
    void checkBounds(Bounds bounds) {
        if (position.x <= 0) { // left side bound checking
            position.x = 0;
        }
        if (position.y <= 0) { // top bound checking
            position.y = 0;
        }
        if (position.x >= bounds.maxWidth - size.width) { // right bound check
            position.x = bounds.maxWidth - size.width;
        }
        if (position.y >= bounds.maxHeight - size.height) {// bottom bound check
            position.y = bounds.maxHeight - size.height;
        }
    } 
    
    /*
        this method returns true if the player collides with any of the blocks
        in the game world.
    */
    
    public boolean hit(Block block) {
        if (block == null) return false;
        
        int X = position.x;
        int Y = position.y;
        
        // if the functional field is true + geometric horizontal checks
        if (block.isFunctional() && X >= block.position.x) {
            if (X <= block.position.x + block.getSize().width) {
                // vertical boundry check
                if (Y >= block.position.y) {
                    if (Y <= block.position.y + block.getSize().height) {
                        // if all the statement above evaluates to true
                        // then it mean the Player is hitting a block
                        // that blocks is set to not functional
                        block.setFunctional(false);
                        return true;
                    }
                }                
            }
        }
        return false; // if doesn't collide
    }
    
    @Override
    public void update() {
        // checks if the ball is in the boundry
        checkBounds(bounds);
    }

    @Override
    public void render(Graphics gfx) {
        // drawing three nested circles inside one another, with different size
        // and colors
        gfx.setColor(Color.ORANGE);
        gfx.fillOval(position.x , position.y, size.width, size.height);

        gfx.setColor(Color.GREEN);
        gfx.fillOval(position.x+3, position.y+3, size.width-6, size.height-6);
        
        gfx.setColor(Color.CYAN);
        gfx.fillOval(position.x+6, position.y+6, size.width-12, size.height-12);

    }
}


/*

    the GameObject class is an abstract class, defining all the necessery
    properties for other game components (Player, Block).
    the abstract fields inside the class needs to initalized in the class
    extending the GameObject
*/


abstract class GameObject {
    
    protected class Position { 
        int x, y; 
        Position(int x, int y) {
            this.x = x; this.y = y;
        }
    }
    protected class Velocity { 
        float dx, dy; 
        Velocity(float dx, float dy) {
            this.dx = dx; this.dy = dy;
        }
    }
    protected class Size {
        int width, height;
        Size(int width, int height) {
            this.width = width; this.height = height;
        }
    }
    
    protected class Bounds {
        int maxWidth, maxHeight;
        Bounds(int maxWidth, int maxHeight) {
            this.maxWidth = maxWidth; this.maxHeight = maxHeight;
        }
    }
    
    protected abstract class Control extends MouseAdapter {};
    
    protected Position position;
    protected Velocity velocity;
    protected Control control;
    protected Size size;
    protected Bounds bounds;

        
    void setPosition(int x, int y) {
        if (position == null)
            throw new Error("Property not initialized");
        position.x = x; 
        position.y = y;
    }
    
    void setSize(int width, int height) {
        if (size == null)
            throw new Error("Property not initialized");
        size.width = width;
        size.height = height;
    }
    
    void setVelocity(float dx, float dy) {
        if (velocity != null)
            throw new Error("Property not initialized");
        velocity.dx = dx;
        velocity.dy = dy;
    } 
    
    void setBounds(int width, int height) {
        if (bounds == null)
            throw new Error("Property not initialized");
        bounds.maxWidth = width;
        bounds.maxHeight = height;
    }
    
    Position getPosition() { return position; }
    Velocity getVelocity() { return velocity; }
    Control getControl() { return control; }
    Size getSize() { return size; }
    Bounds getBounds() { return bounds; }
    
    
    protected abstract void update();    
    protected abstract void render(Graphics gfx);
}