package com.azavea.rf.datamodel

import java.io._

import io.circe._
import io.circe.syntax._
import io.circe.generic.JsonCodec
import spire.syntax.cfor._
import geotrellis.raster.{MultibandTile, _}
import geotrellis.raster.equalization.HistogramEqualization
import geotrellis.raster.histogram.Histogram
import geotrellis.raster.sigmoidal.SigmoidalContrast
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._

import scala.math._
import scala.annotation.tailrec
import com.azavea.rf.datamodel.color._
import com.azavea.rf.datamodel.util.TimingLogging
import com.azavea.rf.tool.eval.LazyTile
import geotrellis.raster._
import geotrellis.raster.histogram._


object SigmoidalContrastLazy {

  private def transform(cellType: CellType, alpha: Double, beta: Double)(intensity: Double): Double = {
    val bits = cellType.bits

    val u = cellType match {
      case _: FloatCells =>
        (intensity - Float.MinValue)/(Float.MaxValue - Float.MinValue)
      case _: DoubleCells =>
        (intensity/2 - Double.MinValue/2)/(Double.MaxValue/2 - Double.MinValue/2)
      case _: BitCells | _: UByteCells | _: UShortCells =>
        (intensity / ((1<<bits)-1))
      case _: ByteCells | _: ShortCells | _: IntCells =>
        (intensity + (1<<(bits-1))) / ((1<<bits)-1)
    }

    val numer = 1/(1+math.exp(beta*(alpha-u))) - 1/(1+math.exp(beta))
    val denom = 1/(1+math.exp(beta*(alpha-1))) - 1/(1+math.exp(beta*alpha))
    val gu = math.max(0.0, math.min(1.0, numer / denom))

    cellType match {
      case _: FloatCells =>
        (Float.MaxValue * (2*gu - 1.0))
      case _: DoubleCells =>
        (Double.MaxValue * (2*gu - 1.0))
      case _: BitCells | _: UByteCells | _: UShortCells =>
        ((1<<bits) - 1) * gu
      case _: ByteCells | _: ShortCells | _: IntCells =>
        (((1<<bits) - 1) * gu) - (1<<(bits-1))
    }
  }

  def apply(tile: LazyTile, cellType: CellType, alpha: Double, beta: Double): LazyTile = {
    val localTransform = transform(cellType, alpha, beta)_
    tile.mapDouble(localTransform)
  }

  def apply(tile: Vector[(LazyTile, Int)], cellType: CellType, alpha: Double, beta: Double): Vector[(LazyTile, Int)] = {
    val localTransform = transform(cellType, alpha, beta)_
    tile.map { case (t, i) => t.mapDouble(localTransform) -> i }
  }
}


object SaturationAdjust {
  def apply(rgbTile: MultibandTile, chromaFactor: Double): MultibandTile = {
    scaleTileChroma(rgbTile, chromaFactor)
  }

  def apply(rgbTile: Vector[(LazyTile, Int)], cellType: CellType, chromaFactor: Double): Vector[(LazyTile, Int)] = {
    scaleTileChroma(rgbTile, cellType, chromaFactor)
  }

  /* Convert RGB to Hue, Chroma, Luma https://en.wikipedia.org/wiki/HSL_and_HSV#Lightness */
  def scaleTileChroma(rgbTile: MultibandTile, chromaFactor: Double): MultibandTile = {
    val (red, green, blue) = (rgbTile.band(0), rgbTile.band(1), rgbTile.band(2))
    val (nred, ngreen, nblue) = (
      ArrayTile.alloc(rgbTile.cellType, rgbTile.cols, rgbTile.rows),
      ArrayTile.alloc(rgbTile.cellType, rgbTile.cols, rgbTile.rows),
      ArrayTile.alloc(rgbTile.cellType, rgbTile.cols, rgbTile.rows)
    )
    cfor(0)(_ < rgbTile.cols, _ + 1) { col =>
      cfor(0)(_ < rgbTile.rows, _ + 1) { row =>
        val (r, g, b) = (red.get(col, row), green.get(col, row), blue.get(col, row))
        val (hue, chroma, luma) = RGBToHCLuma(r, g, b)
        val newChroma = scaleChroma(chroma, chromaFactor)
        val (nr, ng, nb) = HCLumaToRGB(hue, newChroma, luma)
        nred.set(col, row, nr)
        ngreen.set(col, row, ng)
        nblue.set(col, row, nb)
      }
    }
    MultibandTile(nred, ngreen, nblue)
  }

  def scaleTileChroma(rgbTile: Vector[(LazyTile, Int)], cellType: CellType, chromaFactor: Double): Vector[(LazyTile, Int)] = {
    val tiles = rgbTile.map(_._1)
    val (red, green, blue) = (tiles(0), tiles(1), tiles(2))
    val (nred, ngreen, nblue) = (
      ArrayTile.alloc(cellType, red.cols, red.rows),
      ArrayTile.alloc(cellType, red.cols, red.rows),
      ArrayTile.alloc(cellType, red.cols, red.rows)
    )
    cfor(0)(_ < red.cols, _ + 1) { col =>
      cfor(0)(_ < red.rows, _ + 1) { row =>
        val (r, g, b) = (red.get(col, row), green.get(col, row), blue.get(col, row))
        val (hue, chroma, luma) = RGBToHCLuma(r, g, b)
        val newChroma = scaleChroma(chroma, chromaFactor)
        val (nr, ng, nb) = HCLumaToRGB(hue, newChroma, luma)
        nred.set(col, row, nr)
        ngreen.set(col, row, ng)
        nblue.set(col, row, nb)
      }
    }
    Vector(nred, ngreen, nblue).map(LazyTile(_)).zipWithIndex
  }

  // See link for a detailed explanation of what is happening. Basically we are taking the
  // RGB cube and tilting it on its side so that the black -> white line runs vertically
  // up the center of the HCL cylinder, then flattening the cube down into a hexagon and then
  // pretending that that hexagon is actually a cylinder.
  // https://en.wikipedia.org/wiki/HSL_and_HSV#Lightness
  def RGBToHCLuma(rByte: Int, gByte: Int, bByte: Int): (Double, Double, Double) = {
    // RGB come in as unsigned Bytes, but the transformation requires Doubles [0,1]
    val (r, g, b) = (rByte / 255.0, gByte / 255.0, bByte / 255.0)
    val colors = List(r, g, b)
    val max = colors.max
    val min = colors.min
    val chroma = max - min
    val hueSextant = ((chroma, max) match {
      case (0, _) => 0 // Technically, undefined, but we'll ignore this value in this case.
      case (_, x) if x == r => ((g - b) / chroma.toDouble) % 6
      case (_, x) if x == g => ((b - r) / chroma.toDouble) + 2
      case (_, x) if x == b => ((r - g) / chroma.toDouble) + 4
    })
    // Wrap degrees
    val hue = ((hueSextant * 60.0) % 360) match {
      case h if h < 0 => h + 360
      case h if h >= 0 => h
    }
    // Perceptually weighted average of "lightness" contribution of sRGB primaries (it's
    // not clear that we're in sRGB here, but that's the default for most images intended for
    // display so it's a good guess in the absence of explicit information).
    val luma = 0.21*r + 0.72*g + 0.07*b
    (hue, chroma, luma)
  }

  // Reverse the process above
  def HCLumaToRGB(hue: Double, chroma: Double, luma: Double): (Int, Int, Int) = {
    val sextant = hue / 60.0
    val X = chroma * (1 - Math.abs((sextant % 2) - 1))
    // Projected color values, i.e., on the flat projection of the RGB cube
    val (rFlat:Double, gFlat:Double, bFlat:Double) = ((chroma, sextant) match {
      case (0.0, _) => (0.0, 0.0, 0.0) // Gray (or black / white)
      case (_, s) if 0 <= s && s < 1 => (chroma, X, 0.0)
      case (_, s) if 1 <= s && s < 2 => (X, chroma, 0.0)
      case (_, s) if 2 <= s && s < 3 => (0.0, chroma, X)
      case (_, s) if 3 <= s && s < 4 => (0.0, X, chroma)
      case (_, s) if 4 <= s && s < 5 => (X, 0.0, chroma)
      case (_, s) if 5 <= s && s < 6 => (chroma, 0.0, X)
    })

    // We can add the same value to each component to move straight up the cylinder from the projected
    // plane, back to the correct lightness value.
    val lumaCorrection = luma - (0.21*rFlat + 0.72*gFlat + 0.07*bFlat)
    val r = clamp8Bit((255 * (rFlat + lumaCorrection)).toInt)
    val g = clamp8Bit((255 * (gFlat + lumaCorrection)).toInt)
    val b = clamp8Bit((255 * (bFlat + lumaCorrection)).toInt)
    (r, g, b)
  }

  def scaleChroma(chroma: Double, scaleFactor: Double): Double = {
    // Chroma is a Double in the range [0.0, 1.0]. Scale factor is the same as our other gamma corrections:
    // a Double in the range [0.0, 2.0].
    val scaled = math.pow(chroma, 1.0 / scaleFactor)
    if (scaled < 0.0) 0.0
    else if (scaled > 1.0) 1.0
    else scaled
  }

  @inline def clamp8Bit(z: Int): Int = {
    if (z < 0) 0
    else if (z > 255) 255
    else z
  }
}

case class Memo[I <% K, K, O](f: I => O) extends (I => O) {
  import collection.mutable.{Map => Dict}
  type Input = I
  type Key = K
  type Output = O

  val cache = Dict.empty[K, O]
  override def apply(x: I) = cache.getOrElseUpdate(x, f(x))
}

object WhiteBalance {
  
  @inline def clamp8Bit(z: Int): Int = {
    if (z < 0) 0
    else if (z > 255) 255
    else z
  }

  def apply(rgbTiles: List[MultibandTile]): List[MultibandTile] = {
    val tileAdjs = rgbTiles.par.map(t => tileRgbAdjustments(t)).toList.foldLeft((0.0, 0.0, 0.0))((acc, x) => {
      (acc._1 + (x._1 / rgbTiles.length), acc._2 + (x._2 / rgbTiles.length), acc._3 + (x._3 / rgbTiles.length))
    })

    val newTiles =
      rgbTiles
        .map(t => MultibandTile(
          t.band(0).mapIfSet(c => clamp8Bit((c * tileAdjs._1).toInt)),
          t.band(1).mapIfSet(c => clamp8Bit((c * tileAdjs._2).toInt)),
          t.band(2).mapIfSet(c => clamp8Bit((c * tileAdjs._3).toInt))))
        .map(_.convert(UByteConstantNoDataCellType))

    newTiles
  }

  def tileRgbAdjustments(rgbTile: MultibandTile): (Double, Double, Double) = {
    try {
      val resampledTile = rgbTile.resample(128, 128)
      val newTileAdjs = adjustGrey(resampledTile, (1.0, 1.0, 1.0), 0, false)
      newTileAdjs
    } catch {
      case ex:Exception => {
        val sw = new StringWriter
        ex.printStackTrace(new PrintWriter(sw))
        println(sw.toString)
        throw ex
      }
    }
  }

  def RgbToYuv(rByte: Int, gByte: Int, bByte: Int): YUV = {
    type I = (Int, Int, Int)
    type K = (Int, Int, Int)
    type O = YUV

    type MemoizedFn = Memo[I, K, O]

    implicit def encode(input: MemoizedFn#Input): MemoizedFn#Key = (input._1, input._2, input._3)

    def rgbToYuv(rb: Int, gb: Int, bb: Int): YUV = {
      val (r, g, b) = (rb, gb, bb)
      val W_r = 0.299
      val W_b = 0.114
      val W_g = 1 - W_r - W_b

      val Y = W_r*r + W_g*g + W_b*b
      val U = -0.168736*r + -0.331264*g + 0.5*b
      val V = 0.5*r + -0.418688*g + -0.081312*b

      YUV(Y,U,V)
    }

    lazy val f: MemoizedFn = Memo {
      case (r, g, b) => rgbToYuv(r,g,b)
      case _ => throw new IllegalArgumentException("Wrong number of arguments")
    }

    f((rByte, gByte, bByte))
  }
  
  case class AutoBalanceParams (
    maxIter: Int, gainIncr:Double, doubleStepThreshold:Double, convergenceThreshold:Double, greyThreshold:Double
  )

  case class YUV (y: Double, u: Double, v: Double)

  val balanceParams = AutoBalanceParams(maxIter=1000, gainIncr=0.01, doubleStepThreshold=0.8, convergenceThreshold=0.001, greyThreshold=0.3)

  def mapBands(mbTile:MultibandTile, f: Array[Int] => Option[YUV]): List[List[Option[YUV]]]  = {
    val newTile = MultibandTile(mbTile.bands)
    val array = Array.ofDim[Option[YUV]](newTile.cols, newTile.rows)

    cfor(0)(_ < newTile.rows, _ + 1) { row =>
      cfor(0)(_ < newTile.cols, _ + 1) { col =>
        val bandValues = Array.ofDim[Int](newTile.bandCount)
        cfor(0)(_ < newTile.bandCount, _ + 1) { band =>
          bandValues(band) = newTile.band(band).get(col, row)
        }
        array(col)(row) = f(bandValues)
      }
    }

    array.map(_.toList).toList
  }
  
  @tailrec
  def adjustGrey(rgbTile:MultibandTile, adjustments:(Double, Double, Double), iter:Int, converged:Boolean):(Double, Double, Double) = {
    if (iter>=balanceParams.maxIter || converged) {
      adjustments
    } else {
      // convert tile to YUV
      val tileYuv = mapBands(rgbTile, (rgb) => {
          val bands = rgb.toList.map((i:Int) => if (isData(i)) Some(i) else None)
          val r :: g :: b :: xs = bands
          val rgbs = (r, g, b)
          val yuv = rgbs match {
            case (Some(rd), Some(gr), Some(bl)) =>  Some(RgbToYuv(rd, gr, bl))
            case _ => None
          }
          yuv
      })
      
      // find grey chromaticity
      val offGrey = (yuv:YUV) => (abs(yuv.u) + abs(yuv.v)) / yuv.y
      val greys = tileYuv.map(lst => lst.map(yuv => {
        for {
          y <- yuv
        } yield {
          if (offGrey(y) < balanceParams.greyThreshold) Some(y) else None
        }
      })).flatten

      // calculate average "off-grey"-ness of U & V
      val uBar = greys.map(yuvs => yuvs.map(mYuv => {
          mYuv match {
            case Some(yuv) => yuv.u
            case _ => 0.0
          }
      })).flatten.sum / greys.length
      
      val vBar = greys.map(yuvs => yuvs.map(mYuv => {
          mYuv match {
            case Some(yuv) => yuv.v
            case _ => 0.0
          }
      })).flatten.sum / greys.length

      // adjust red & blue channels if convergence hasn't been reached
      val err = if (abs(uBar) > abs(vBar)) uBar else vBar
      val gainVal = (err match {
        case x if x < balanceParams.convergenceThreshold => 0
        case x if x > (balanceParams.doubleStepThreshold * 1) => 2 * balanceParams.gainIncr * signum(err)
        case _ => balanceParams.gainIncr * err
      })

      val channelGain = if (abs(vBar) > abs(uBar)) List((1 - gainVal), 1, 1) else List(1, 1, (1 - gainVal))
      val newAdjustments = (adjustments._1 * channelGain(0), adjustments._2 * channelGain(1), adjustments._3 * channelGain(2))

      val balancedTile = MultibandTile(
        rgbTile.band(0).mapIfSet(c => clamp8Bit((c*channelGain(0)).toInt)),
        rgbTile.band(1).mapIfSet(c => clamp8Bit((c*channelGain(1)).toInt)),
        rgbTile.band(2).mapIfSet(c => clamp8Bit((c*channelGain(2)).toInt)))

      adjustGrey(balancedTile, newAdjustments, iter + 1, if (gainVal == 0) true else false)
    }
  }

}

object ColorCorrect extends TimingLogging {
  type LazyMultibandTile = Vector[(LazyTile, Int)]

  // TODO: Now that each correction is a separate class, it should be possible to refactor this object to place the
  // necessary corrections with the classes that enable them. So rather than
  // ```
  // val maybeAdjustContrast =
  //   for (c <- params.brightContrast.contrast)
  //     yield (mb: MultibandTile) => mb.mapBands { (i, tile) => adjustContrast(tile, c) }
  // ```
  //
  // We would do something like `params.brightContrast.adjustContrast(tile)`
  @JsonCodec
  case class Params(
    redBand: Int, greenBand: Int, blueBand: Int,
    gamma: BandGamma,
    bandClipping: PerBandClipping,
    tileClipping: MultiBandClipping,
    sigmoidalContrast: SigmoidalContrast,
    saturation: Saturation,
    equalize: Equalization,
    autoBalance: AutoWhiteBalance
  ) {
    def reorderBands(tile: MultibandTile, hist: Seq[Histogram[Double]]): (MultibandTile, Array[Histogram[Double]]) =
      (tile.subsetBands(redBand, greenBand, blueBand), Array(hist(redBand), hist(greenBand), hist(blueBand)))

    def colorCorrect(tile: MultibandTile, hist: Seq[Histogram[Double]]): MultibandTile = {
      val (rgbTile, rgbHist) = timedCreate("Params", "314::reorderBands start", "314::reorderBands finish") { reorderBands(tile, hist) }
      val result = timedCreate("Params", "315::ColorCorrect start", "315::ColorCorrect finish") {
        fast.ColorCorrect(rgbTile, rgbHist, fast.ColorCorrect.Params(
          redBand, greenBand, blueBand,
          gamma, bandClipping, tileClipping,
          sigmoidalContrast, saturation, equalize,
          autoBalance
        ))
      }
      printBuffer("Params")
      result
    }
  }

  def apply(rgbTile: MultibandTile, rgbHist: Array[Histogram[Double]], params: Params): MultibandTile = {
    val maybeEqualize =
      if (params.equalize.enabled) Some(HistogramEqualization(_: MultibandTile, rgbHist)) else None

    val maybeEqualizeLazy: Option[(LazyMultibandTile) => LazyMultibandTile] =
      if (params.equalize.enabled)
        Some(tiles => {
          HistogramEqualization(MultibandTile(tiles.map(_._1.evaluate.get)), rgbHist).bands.map(LazyTile(_)).zipWithIndex
        })
      else
        None

    case class LayerClipping(redMin: Int, redMax: Int, greenMin: Int, greenMax:Int, blueMin: Int, blueMax: Int)
    sealed trait ClipValue
    case class ClipBounds(min: Int, max: Int) extends ClipValue
    case class MaybeClipBounds(maybeMin: Option[Int], maybeMax: Option[Int]) extends ClipValue
    case class ClippingParams(band: Int, bounds: ClipValue)

    val layerRgbClipping = (for {
      (r :: g :: b :: xs) <- Some(rgbHist.toList)
      isCorrected   <- Some((r :: g :: b :: Nil).foldLeft(true) {(acc, hst) => (
                          acc && (hst match {
                            case x if 1 until 255 contains x.minValue().map(_.toInt).getOrElse(0) => true
                            case x if 1 until 255 contains x.maxValue().map(_.toInt).getOrElse(255) => true
                            case _ => false
                          })
                        )})
      layerClp      <- if (!isCorrected) {
                          val getMin = (hst:Histogram[Double]) => hst.minValue().map(_.toInt).getOrElse(0)
                          val getMax = (hst:Histogram[Double]) => hst.maxValue().map(_.toInt).getOrElse(255)
                          Some(LayerClipping(getMin(r), getMax(r), getMin(g), getMax(g), getMin(b), getMax(b)))
                        } else { None }
    } yield layerClp).getOrElse(LayerClipping(0, 255, 0, 255, 0, 255))

    val rgbBand =
      (specificBand:Option[Int], allBands:Option[Int], tileDefault: Int) =>
        specificBand.fold(allBands)(Some(_)).fold(Some(tileDefault))(x => Some(x))

    val layerNormalizeArgs = Some(
      ClippingParams(0, ClipBounds(layerRgbClipping.redMin, layerRgbClipping.redMax))
        :: ClippingParams(1, ClipBounds(layerRgbClipping.greenMin, layerRgbClipping.greenMax))
        :: ClippingParams(2, ClipBounds(layerRgbClipping.blueMin, layerRgbClipping.blueMax))
        :: Nil
    )

    val colorCorrectArgs = Some(
      ClippingParams(0, MaybeClipBounds(params.bandClipping.redMin, params.bandClipping.redMax))
        :: ClippingParams(1, MaybeClipBounds(params.bandClipping.greenMin, params.bandClipping.greenMax))
        :: ClippingParams(2, MaybeClipBounds(params.bandClipping.blueMin, params.bandClipping.blueMax))
        :: Nil
    )

    val rgbLazyTile: LazyMultibandTile = rgbTile.bands.map(LazyTile(_)).zipWithIndex

    val maybeClipBands =
      (clipParams:Option[List[ClippingParams]]) =>
        for {
          clipParams <- clipParams
        } yield {
          (_ :MultibandTile).mapBands { (i, tile) =>
            (for {
              args <- clipParams.find(cp => cp.band == i)
            } yield {
              args match {
                case ClippingParams(_, ClipBounds(min, max)) => {
                  (for {
                    clipMin <- rgbBand(None, None, min)
                    clipMax <- rgbBand(None, None, max)
                    newMin <- Some(0)
                    newMax <- Some(255)
                  } yield {
                    normalizeAndClamp(tile, clipMin, clipMax, newMin, newMax)
                  })
                }
                case ClippingParams(_, MaybeClipBounds(min, max)) => {
                  (for {
                    clipMin <- rgbBand(min, params.tileClipping.min, 0)
                    clipMax <- rgbBand(max, params.tileClipping.max, 255)
                  } yield {
                    clipBands(tile, clipMin, clipMax)
                  })
                }
              }
            }).flatten.getOrElse(tile)
          }
        }

    val maybeClipBandsLazy: (Option[List[ClippingParams]]) => Option[(LazyMultibandTile) => LazyMultibandTile] =
      (clipParams:Option[List[ClippingParams]]) =>
        for {
          clipParams <- clipParams
        } yield {
          (_ : Vector[(LazyTile, Int)]).map { case (tile, i) =>
            (for {
              args <- clipParams.find(cp => cp.band == i)
            } yield {
              args match {
                case ClippingParams(_, ClipBounds(min, max)) => {
                  (for {
                    clipMin <- rgbBand(None, None, min)
                    clipMax <- rgbBand(None, None, max)
                    newMin <- Some(0)
                    newMax <- Some(255)
                  } yield {
                    normalizeAndClampLazy(tile, clipMin, clipMax, newMin, newMax) -> i
                  })
                }
                case ClippingParams(_, MaybeClipBounds(min, max)) => {
                  (for {
                    clipMin <- rgbBand(min, params.tileClipping.min, 0)
                    clipMax <- rgbBand(max, params.tileClipping.max, 255)
                  } yield {
                    clipBandsLazy(tile, clipMin, clipMax) -> i
                  })
                }
              }
            }).flatten.getOrElse(tile -> i)
          }
        }

    val maybeAdjustGamma =
      for (c <- params.sigmoidalContrast.alpha)
        yield (_: MultibandTile).mapBands { (i, tile) =>
          i match {
            case 0 => params.gamma.redGamma match {
              case None => tile
              case Some(gamma) => gammaCorrect(tile, gamma)
            }
            case 1 => params.gamma.greenGamma match {
              case None => tile
              case Some(gamma) => gammaCorrect(tile, gamma)
            }
            case 2 => params.gamma.blueGamma match {
              case None => tile
              case Some(gamma) => gammaCorrect(tile, gamma)
            }
            case _ =>
              sys.error("Too many bands")
          }
        }

    val maybeAdjustGammaLazy: Option[(LazyMultibandTile) => LazyMultibandTile] =
      for (c <- params.sigmoidalContrast.alpha)
        yield (_: LazyMultibandTile).map { case (tile, i) =>
          i match {
            case 0 => params.gamma.redGamma match {
              case None => tile -> i
              case Some(gamma) => gammaCorrectLazy(tile, gamma) -> i
            }
            case 1 => params.gamma.greenGamma match {
              case None => tile -> i
              case Some(gamma) => gammaCorrectLazy(tile, gamma) -> i
            }
            case 2 => params.gamma.blueGamma match {
              case None => tile -> i
              case Some(gamma) => gammaCorrectLazy(tile, gamma) -> i
            }
            case _ =>
              sys.error("Too many bands")
          }
        }

    val maybeSigmoidal =
      for (alpha <- params.sigmoidalContrast.alpha; beta <- params.sigmoidalContrast.beta)
        yield SigmoidalContrast(_: MultibandTile, alpha, beta)

    val maybeSigmoidalLazy: Option[(LazyMultibandTile) => LazyMultibandTile] =
      for (alpha <- params.sigmoidalContrast.alpha; beta <- params.sigmoidalContrast.beta)
        yield SigmoidalContrastLazy(_: LazyMultibandTile, rgbTile.cellType, alpha, beta)

    val maybeAdjustSaturation =
      for (saturationFactor <- params.saturation.saturation)
        yield SaturationAdjust(_: MultibandTile, saturationFactor)

    val maybeAdjustSaturationLazy: Option[(LazyMultibandTile) => LazyMultibandTile] =
      for (saturationFactor <- params.saturation.saturation)
        yield SaturationAdjust(_: LazyMultibandTile, rgbTile.cellType, saturationFactor)


    // Sequence of transformations to tile, flatten removes None from the list
    val transformations: List[MultibandTile => MultibandTile] = List(
      maybeEqualize,
      maybeClipBands(layerNormalizeArgs),
      maybeAdjustGamma,
      maybeAdjustSaturation,
      maybeSigmoidal,
      maybeClipBands(colorCorrectArgs)
    ).flatten

    val lazyTransformations: List[LazyMultibandTile => LazyMultibandTile] = List(
      maybeEqualizeLazy,
      maybeClipBandsLazy(layerNormalizeArgs),
      maybeAdjustGammaLazy,
      maybeAdjustSaturationLazy,
      maybeSigmoidalLazy,
      maybeClipBandsLazy(colorCorrectArgs)
    ).flatten

    // Apply tile transformations in order from left to right
    MultibandTile(lazyTransformations.foldLeft(rgbLazyTile) { (t, f) => f(t) }.flatMap(_._1.evaluate))
    //transformations.foldLeft(rgbTile){ (t, f) => f(t) }
  }

  @inline def clampColor(z: Int): Int = {
    if (z < 0) 0
    else if (z > 255) 255
    else z
  }

  def maxCellValue(ct: CellType): Int =
    ct match {
      case _: FloatCells =>
        Float.MaxValue.toInt
      case _: DoubleCells =>
        Double.MaxValue.toInt
      case _: BitCells | _: UByteCells | _: UShortCells =>
        (1 << ct.bits) - 1
      case _: ByteCells | _: ShortCells | _: IntCells =>
        ((1 << ct.bits) - 1) - (1 << (ct.bits - 1))
    }

  def clipBandsLazy(tile: LazyTile, min: Int, max: Int): LazyTile = {
    tile.mapIfSet { z =>
      if (z > max) 255
      else if (z < min) 0
      else z
    }
  }

  def clipBands(tile: Tile, min: Int, max: Int): Tile = {
    tile.mapIfSet { z =>
      if (z > max) 255
      else if (z < min) 0
      else z
    }
  }

  def normalizeAndClampLazy(tile: LazyTile, oldMin: Int, oldMax: Int, newMin: Int, newMax: Int): LazyTile = {
    val dNew = newMax - newMin
    val dOld = oldMax - oldMin

    // When dOld is nothing (normalization is meaningless in this context), we still need to clamp
    if (dOld == 0) tile.mapIfSet { z =>
      if (z > newMax) newMax
      else if (z < newMin) newMin
      else z
    } else tile.mapIfSet { z =>
      val scaled = (((z - oldMin) * dNew) / dOld) + newMin

      if (scaled > newMax) newMax
      else if (scaled < newMin) newMin
      else scaled
    }
  }

  def normalizeAndClamp(tile: Tile, oldMin: Int, oldMax: Int, newMin: Int, newMax: Int): Tile = {
    val dNew = newMax - newMin
    val dOld = oldMax - oldMin

    // When dOld is nothing (normalization is meaningless in this context), we still need to clamp
    if (dOld == 0) tile.mapIfSet { z =>
      if (z > newMax) newMax
      else if (z < newMin) newMin
      else z
    } else tile.mapIfSet { z =>
      val scaled = (((z - oldMin) * dNew) / dOld) + newMin

      if (scaled > newMax) newMax
      else if (scaled < newMin) newMin
      else scaled
    }
  }

  def gammaCorrectLazy(tile: LazyTile, gamma: Double): LazyTile =
    tile.mapIfSet { z =>
      clampColor {
        val gammaCorrection = 1 / gamma
        (255 * math.pow(z / 255.0, gammaCorrection)).toInt
      }
    }

  def gammaCorrect(tile: Tile, gamma: Double): Tile =
    tile.mapIfSet { z =>
      clampColor {
        val gammaCorrection = 1 / gamma
        (255 * math.pow(z / 255.0, gammaCorrection)).toInt
      }
    }
}
