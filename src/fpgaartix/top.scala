package artix

import chisel3._
import chisel3.iotesters._
import RV32_3stage.Constants._
import scala.collection.mutable.HashMap
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._
import freechips.rocketchip.amba.axi4._

object ReferenceChipBackend {
  val initMap = new HashMap[Module, Bool]()
}

case class MasterConfig(base: Long, size: Long, beatBytes: Int, idBits: Int)
case object ExtMem extends Field[MasterConfig]
case object MMIO extends Field[MasterConfig]
class WithArtixAdapter extends Config((site, here, up) => {
  case ExtMem => MasterConfig(base= 0x10000000L, size= 0x10000000L, beatBytes= 4, idBits= 4)
  case MMIO => MasterConfig(base= 0x40000000L, size= 0x10000L, beatBytes= 4, idBits= 4)
  case Common.xprlen => 32
  case Common.usingUser => false
  case NUM_MEMORY_PORTS => 2
  case PREDICT_PCP4 => true
  case Common.PRINT_COMMIT_LOG => false
})

class Top extends Module {
  val inParams = new WithArtixAdapter
  val tile = LazyModule(new SodorTile()(inParams)).module
  val io = IO(new Bundle {
    val mem_axi = tile.mem_axi4.cloneType
  })
  io.mem_axi <> tile.mem_axi4
}

object elaborate extends ChiselFlatSpec{
  def main(args: Array[String]): Unit = {
    implicit val inParams = new WithArtixAdapter
    chisel3.Driver.execute(args, () => new Top)
  }
}