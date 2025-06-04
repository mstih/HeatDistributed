package org.mihastih;

import mpi.*;

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
        System.out.println("Rank: " + rank);
        System.out.println("Size: " + size);

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
        System.out.println("Width: "+ width);
        System.out.println("Height: "+ height);
        System.out.println("Number of points: " + numPoints);
        MPI.Finalize();
    }


}
