package artix
{

import chisel3._
import Common._  
import zynq._ 
import util._
import RV32_3stage._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.util._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.config._

class FIFOtoDMI()(implicit p: Parameters) extends Module {
  val io = new Bundle{
    val dmi = Flipped(new DMIIO())
  }
}

class SodorTileModule(outer: SodorTile)(implicit p: Parameters) extends LazyModuleImp(outer){
  val mem_axi4 = IO(HeterogeneousBag.fromNode(outer.mem_axi4.in))

  val core   = Module(new Core())
  val fifotodmi   = Module(new FIFOtoDMI())
  val memory = outer.memory.module 
  val debug = Module(new DebugModule())

  mem_axi4.head <> outer.mem_axi4.in.head._1

  core.reset := debug.io.resetcore | reset.toBool

  core.io.dmem <> memory.io.core_ports(0)
  core.io.imem <> memory.io.core_ports(1)
  debug.io.debugmem <> memory.io.debug_port

  // DTM memory access
  debug.io.ddpath <> core.io.ddpath
  debug.io.dcpath <> core.io.dcpath 
  debug.io.dmi <> fifotodmi.io.dmi
}

class SodorTile(implicit p: Parameters) extends LazyModule
{
  val memory = LazyModule(new MemAccessToTL(num_core_ports=2))
  lazy val module = Module(new SodorTileModule(this))
  private val device = new MemoryDevice
  val config = p(ExtMem)
  val mmio = p(MMIO)
  val mem_axi4 = AXI4SlaveNode(Seq(  
    AXI4SlavePortParameters(
      slaves = Seq(AXI4SlaveParameters(
              address       = Seq(AddressSet(config.base, config.size-1),AddressSet(mmio.base, mmio.size-1)),
              resources     = device.reg,
              regionType    = RegionType.UNCACHED,   // cacheable
              executable    = false,
              supportsWrite = TransferSizes(1, 4), // The slave supports 1-256 byte transfers
              supportsRead  = TransferSizes(1, 4),
              interleavedId = Some(0))),             // slave does not interleave read responses
            beatBytes = 4,minLatency =0)
  ))
   
  val tlxbar = LazyModule(new TLXbar)

  (mem_axi4 
        := AXI4Buffer()
        := AXI4UserYanker(Some(2))
        := AXI4IdIndexer(4)
        := TLToAXI4()
        := tlxbar.node)

  tlxbar.node := memory.masterDebug
  tlxbar.node := memory.masterInstr
  tlxbar.node := memory.masterData 

}
 
}
