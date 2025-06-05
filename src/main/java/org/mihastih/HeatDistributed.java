package org.mihastih;

import mpi.*;

import java.util.Random;

public class HeatDistributed {
    private static int width = 800;
    private static int height = 600;
    private static int numPoints = 3;
    private static int[][] grid;
    private static final long SEED = 5318008;
    private static final int BRUSH_SIZE = 20;
    private static final int MAX_TEMP = 255;

    public static void main(String[] args){
        MPI.Init(args);

        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();
        //System.out.println("Rank: " + rank);
        //System.out.println("Size: " + size);

        for (int i = 0; i < args.length; i++) {
            if("width".equals(args[i]) && i + 1 < args.length) {
                width = Integer.parseInt(args[i+1]);
                i++;
            } else if("height".equals(args[i]) && i + 1 < args.length) {
                height = Integer.parseInt(args[i+1]);
                i++;
            } else if("points".equals(args[i]) && i + 1 < args.length) {
                numPoints = Integer.parseInt(args[i+1]);
                i++;
            }
        }

        grid = new int[width][height];
//        System.out.println("Width: "+ width);
//        System.out.println("Height: "+ height);
//        System.out.println("Number of points: " + numPoints);

        // Main process adds random heat points
        if(rank == 0){
            Random random = new Random(SEED);
            for (int i = 0; i < numPoints; i++) {
                int x = random.nextInt(width);
                int y = random.nextInt(height);
                //grid[x][y] = MAX_TEMP;
                addHeat(x, y);
            }
        }

        // Send grid to all
        for(int i = 0; i < width; i++){
            MPI.COMM_WORLD.Bcast(grid[i], 0, height, MPI.INT, 0);
        }

        // Split the grid for workers
//        int rowsPerWorker = width / size;
//        int start = rank * rowsPerWorker;
//        int end = (rank == size - 1) ? width : start + rowsPerWorker;
        int[] startAndCount = computeStartAndCount(rank, size, width);
        int start = startAndCount[0];
        int numRows = startAndCount[1];
        int end = start + numRows;

        int[] recvCount = new int[size];
        int[] displs = new int[size];
        if(rank==0){
            int disp = 0;
            for(int i = 0; i < size; i++){
                int[] sAndC = computeStartAndCount(i, size, width);
                recvCount[i] = sAndC[1] * height;
                displs[i] = disp;
                disp += recvCount[i];
            }
        }

        //Start timer on master
        long startTime = 0;
        if(rank == 0){
            System.out.println("Starting Distributed simulation(" + size + " worker threads)...");
            startTime = System.currentTimeMillis();
        }

        boolean[] convergedArray = new boolean[1];

        // Main loop
        while(true){
            int[][] newGrid = new int[width][height];

            for(int i = start; i < end; i++){
                for(int j =0; j<height; j++){
                    newGrid[i][j] = calculateNewTemperature(i, j);
                }
            }

            //Calc number of rows and send them as array
            int numRows1 = end - start;
            int[] flatSendBuf = new int[numRows1 * height];

            // Add rows to flat buffer
            for (int i = 0; i < numRows1; i++) {
                System.arraycopy(newGrid[start + i], 0, flatSendBuf, i * height, height);
            }

            int[] flatRecvBuf = null;
            if (rank == 0) {
                flatRecvBuf = new int[width * height];  // total rows × columns
            }

            // Get all the results from workers into flatrecvbuf
            MPI.COMM_WORLD.Gatherv(
                    flatSendBuf, 0, flatSendBuf.length, MPI.INT,
                    flatRecvBuf, 0, recvCount, displs, MPI.INT, 0
            );

            // Copy them beck to original grid
            if (rank == 0) {
                for (int i = 0; i < width; i++) {
                    System.arraycopy(flatRecvBuf, i * height, grid[i], 0, height);
                }
            }

            if(rank == 0){
                convergedArray[0] = isDone();
            }

            MPI.COMM_WORLD.Bcast(convergedArray, 0, 1, MPI.BOOLEAN, 0);
            if(convergedArray[0]) break;

            // If not send again
            for(int i = 0; i < width; i++){
                MPI.COMM_WORLD.Bcast(grid[i], 0, height, MPI.INT, 0);
            }
        }


        // Stop the timer and display time taken for computation
        if(rank == 0){
            long endTime = System.currentTimeMillis();
            System.out.println("Time taken: " + (endTime - startTime) + "ms");
        }
        MPI.Finalize();
    }

    private static boolean isDone() {
        int temperature = grid[0][0];
        for (int[] row : grid) {
            for (int cell : row) {
                if (cell != temperature) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int calculateNewTemperature(int x, int y) {
        if (x == 0 || x == width - 1 || y == 0 || y == height - 1) {
            return 0;
        }

        int totalTemp = grid[x][y]; // Include the center cell's temperature
        int totalCells = 1; // Count the center cell

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                int nx = x + dx;
                int ny = y + dy;

                // Skip the center cell
                if (dx == 0 && dy == 0) {
                    continue;
                }

                // Check if the neighbor cell is within the grid
                if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                    totalTemp += grid[nx][ny];
                    totalCells++;
                }
            }
        }
        return totalTemp / totalCells; // average temperature
    }

    // To calculate the start and end for each worker
    private static int[] computeStartAndCount(int rank, int size, int width){
        int base = width / size;
        int remainder = width % size;
        int start = rank * base + Math.min(rank, remainder);
        int count = base + (rank < remainder ? 1 : 0);
        return new int[]{start, count};
    }

    private static void addHeat(int x, int y) {
        System.out.println("Adding heat at (" + x + ", " + y + ")");

        int minX = Math.max(0, x - BRUSH_SIZE);
        int minY = Math.max(0, y - BRUSH_SIZE);
        int maxX = Math.min(width - 1, x + BRUSH_SIZE);
        int maxY = Math.min(height - 1, y + BRUSH_SIZE);

        for (int i = minX; i <= maxX; i++) {
            for (int j = minY; j <= maxY; j++) {
                int dx = i - x;
                int dy = j - y;
                if (dx * dx + dy * dy <= BRUSH_SIZE * BRUSH_SIZE) {
                    grid[i][j] = MAX_TEMP; // maximum temperature
                }
            }
        }
    }
}
