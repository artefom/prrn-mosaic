package backend.rasterio;

import backend.utils.Vec2d;
import backend.utils.Vec2i;
import org.gdal.gdal.Band;
import org.gdal.gdal.Dataset;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

/**
 * Image reader is geo-spatial pixel reader.
 * Image reader provides tools for reading from Datasets.
 * It's main purpose is to read multiple files with same geographic extents
 *
 * It reads data block by block
 */
public class ImageReader {

    private ArrayList<RasterDataset> datasets;
    private RasterGrid rgrid;

    private Vec2i totalblocks;
    private Vec2i windowsize;

    // private int overlap;


    private ImageReader(RasterGrid area_to_read) {
        datasets = new ArrayList<>();
        rgrid = area_to_read;

        set_window_size( 512,512 );
    }

    /**
     * Calculate block positions before read
     */
    public void init() {

        // workout area to be read
        Vec2i total_size = get_total_size();

        // total number of blocks
        totalblocks = new Vec2i(
                (int)(Math.ceil( (double)(total_size.x) / windowsize.x)),
                (int)(Math.ceil( (double)(total_size.y) / windowsize.y))
        );
    }

    public Vec2i get_total_size() {
        return new Vec2i(rgrid.get_width(),rgrid.get_height());
    }

    public void add_dataset(RasterDataset ds) {
        datasets.add(ds);
    }

    /**
     * Set block size
     * @param width
     * @param height
     */
    public void set_window_size(int width, int height) {
        windowsize = new Vec2i();
        windowsize.x = width;
        windowsize.y= height;
    }

    public int get_y_total_blocks() {
        return totalblocks.y;
    }

    public int get_x_total_blocks() {
        return totalblocks.x;
    }

    /**
     * Read specific block by it's x and y position
     * @param xblock
     * @param yblock
     */
    public BlockInfo read_block(int xblock, int yblock) {
        BlockInfo info = new BlockInfo();

        // Set total blocks to info
        info.set_totalblocks(totalblocks.x,totalblocks.y);

        Vec2i total_size = get_total_size();

        // calculate the coords of this block in pixels
        Vec2i top_coord = new Vec2i(
                xblock * windowsize.x,
                yblock * windowsize.y);
        Vec2i bot_coord = new Vec2i(
                (xblock+1)*windowsize.x,
                (yblock+1)*windowsize.y);

        if (bot_coord.x > total_size.x) bot_coord.y=total_size.x;
        if (bot_coord.y > total_size.y) bot_coord.y = total_size.y;



        // convert them into real-world coordinates
        Vec2d block_tl = rgrid.pix2wld(top_coord);
        Vec2d block_br = rgrid.pix2wld(bot_coord);
        info.set_block_bounds(block_tl,block_br);

        // calculate block size

        Vec2i block_size = new Vec2i(bot_coord.x-top_coord.x,bot_coord.y-top_coord.y);
        info.set_block_size(block_size.x,block_size.y);

        return info;
    };

    public static double[][] read_block(Dataset ds, int xoff, int yoff, int xsize, int ysize) {

        int raster_size = xsize*ysize;
        int n_layers = ds.getRasterCount();

        double[][] ret = new double[n_layers][raster_size];

        for (int layer_n = 0; layer_n != ds.getRasterCount(); ++layer_n) {
            Band b = ds.GetRasterBand(layer_n+1);
            b.ReadRaster(xoff,yoff,xsize,ysize,ret[layer_n]);
        }

        return ret;
    }


    // STATIC FACTORIES


    /**
     * Creates Image reader with single image for
     * @param filename
     */
    public static ImageReader from_file(String filename) throws IOException {
        RasterDataset ds = RasterDataset.from_file(filename);
        ImageReader reader = new ImageReader(ds.grid());
        reader.add_dataset(ds);
        return reader;
    }

    /**
     * Creates image reader with single dataset
     * @param ds dataset to read from
     */
    public static ImageReader from_dataset(RasterDataset ds) {
        ImageReader reader = new ImageReader(ds.grid().clone());
        reader.add_dataset(ds);
        return reader;
    }

    /**
     * Create image reader from multiple datasets
     * Reads their intersection
     */
    public static ImageReader from_datasets(Collection<RasterDataset> datasets) {

        // Find intersection area
        RasterGrid intersection_grid = datasets.iterator().next().grid().clone();
        for (RasterDataset ds : datasets) {
            intersection_grid = intersection_grid.intersection(ds.grid());
        }

        ImageReader reader = new ImageReader(intersection_grid);

        return reader;
    }

    public static ImageReader from_datasets(RasterDataset... datasets) {
        return ImageReader.from_datasets(Arrays.asList(datasets));
    }

}