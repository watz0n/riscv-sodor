package artix

import chisel3._
import chisel3.iotesters._
import zynq._
import RV32_3stage.Constants._
import scala.collection.mutable.HashMap
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._
import freechips.rocketchip.amba.axi4._

class WithArtixAdapter extends Config((site, here, up) => {
  case ExtMem => MasterConfig(base= 0x10000000L, size= 0x10000000L, beatBytes= 4, idBits= 4)
  case MMIO => MasterConfig(base= 0x40000000L, size= 0x10000L, beatBytes= 4, idBits= 4)
  case Common.xprlen => 32
  case Common.usingUser => false
  case NUM_MEMORY_PORTS => 2
  case PREDICT_PCP4 => true
  case Common.PRINT_COMMIT_LOG => false
})

class glip_uart_toplevel extends BlackBox(Map("FREQ_CLK_IO" -> chisel3.core.IntParam(50000000),
                                  "BAUD" -> chisel3.core.IntParam(115200),"WIDTH" -> chisel3.core.IntParam(8))) {
  val io = IO(new Bundle {
    val clk_io = Input(Clock())
    val clk = Input(Clock())
    val rst = Input(Bool())
    val com_rst = Output(Bool())
    val fifo_out_data = Input(UInt(8.W)) // WIDTH
    val fifo_out_valid = Input(Bool())
    val fifo_out_ready = Output(Bool())
    val fifo_in_data = Output(UInt(8.W)) // WIDTH
    val fifo_in_valid = Output(Bool())
    val fifo_in_ready = Input(Bool())
    val ctrl_logic_rst = Output(Bool())
    val uart_rx = Input(Bool())
    val uart_tx = Output(Bool())
    val uart_cts_n = Input(Bool())
    val uart_rts_n = Output(Bool())
    val error = Output(Bool())
  })
}

class Top extends Module {
  val inParams = new WithArtixAdapter
  val tile = LazyModule(new SodorTile()(inParams)).module
  val uart = Module(new glip_uart_toplevel())
  val io = IO(new Bundle {
    val mem_axi = tile.mem_axi4.cloneType
    val rxd = Input(Bool())
    val txd = Output(Bool())
  })
  io.mem_axi <> tile.mem_axi4
  io.txd := uart.io.uart_tx
  uart.io.uart_rx := io.rxd
  uart.io.clk := clock 
  uart.io.clk_io := clock
  uart.io.rst := reset
  uart.io.fifo_out_data := tile.fifo_out.bits.data
  uart.io.fifo_out_valid := tile.fifo_out.valid 
  tile.fifo_out.ready := uart.io.fifo_out_ready 
  tile.fifo_in.bits.data := uart.io.fifo_in_data
  tile.fifo_in.valid := uart.io.fifo_in_valid
  uart.io.fifo_in_ready := tile.fifo_in.ready
  uart.io.uart_cts_n := 0.U
}

object elaborate extends ChiselFlatSpec{
  def main(args: Array[String]): Unit = {
    implicit val inParams = new WithArtixAdapter
    chisel3.Driver.execute(args, () => new Top)
  }
}