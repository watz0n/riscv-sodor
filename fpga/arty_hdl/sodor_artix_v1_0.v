
`timescale 1 ns / 1 ps

	module sodor_artix_v1_0 #
	(
		// Users to add parameters here

		// User parameters ends
		// Do not modify the parameters beyond this line


		// Parameters of Axi Master Bus Interface M00_AXI
		parameter integer C_M00_AXI_ID_WIDTH	= 4,
		parameter integer C_M00_AXI_ADDR_WIDTH	= 32,
		parameter integer C_M00_AXI_DATA_WIDTH	= 32,
		parameter integer C_M00_AXI_AWUSER_WIDTH	= 0,
		parameter integer C_M00_AXI_ARUSER_WIDTH	= 0,
		parameter integer C_M00_AXI_WUSER_WIDTH	= 0,
		parameter integer C_M00_AXI_RUSER_WIDTH	= 0,
		parameter integer C_M00_AXI_BUSER_WIDTH	= 0
	)
	(
		// Users to add ports here

		// User ports ends
		// Do not modify the ports beyond this line


		// Ports of Axi Master Bus Interface M00_AXI
		//input wire  m00_axi_init_axi_txn,
		//output wire  m00_axi_txn_done,
		//output wire  m00_axi_error,
		input wire  m00_axi_aclk,
		input wire  m00_axi_aresetn,
		output wire [C_M00_AXI_ID_WIDTH-1 : 0] m00_axi_awid,
		output wire [C_M00_AXI_ADDR_WIDTH-1 : 0] m00_axi_awaddr,
		output wire [7 : 0] m00_axi_awlen,
		output wire [2 : 0] m00_axi_awsize,
		output wire [1 : 0] m00_axi_awburst,
		output wire  m00_axi_awlock,
		output wire [3 : 0] m00_axi_awcache,
		output wire [2 : 0] m00_axi_awprot,
		output wire [3 : 0] m00_axi_awqos,
		output wire [C_M00_AXI_AWUSER_WIDTH-1 : 0] m00_axi_awuser,
		output wire  m00_axi_awvalid,
		input wire  m00_axi_awready,
		output wire [C_M00_AXI_DATA_WIDTH-1 : 0] m00_axi_wdata,
		output wire [C_M00_AXI_DATA_WIDTH/8-1 : 0] m00_axi_wstrb,
		output wire  m00_axi_wlast,
		output wire [C_M00_AXI_WUSER_WIDTH-1 : 0] m00_axi_wuser,
		output wire  m00_axi_wvalid,
		input wire  m00_axi_wready,
		input wire [C_M00_AXI_ID_WIDTH-1 : 0] m00_axi_bid,
		input wire [1 : 0] m00_axi_bresp,
		input wire [C_M00_AXI_BUSER_WIDTH-1 : 0] m00_axi_buser,
		input wire  m00_axi_bvalid,
		output wire  m00_axi_bready,
		output wire [C_M00_AXI_ID_WIDTH-1 : 0] m00_axi_arid,
		output wire [C_M00_AXI_ADDR_WIDTH-1 : 0] m00_axi_araddr,
		output wire [7 : 0] m00_axi_arlen,
		output wire [2 : 0] m00_axi_arsize,
		output wire [1 : 0] m00_axi_arburst,
		output wire  m00_axi_arlock,
		output wire [3 : 0] m00_axi_arcache,
		output wire [2 : 0] m00_axi_arprot,
		output wire [3 : 0] m00_axi_arqos,
		output wire [C_M00_AXI_ARUSER_WIDTH-1 : 0] m00_axi_aruser,
		output wire  m00_axi_arvalid,
		input wire  m00_axi_arready,
		input wire [C_M00_AXI_ID_WIDTH-1 : 0] m00_axi_rid,
		input wire [C_M00_AXI_DATA_WIDTH-1 : 0] m00_axi_rdata,
		input wire [1 : 0] m00_axi_rresp,
		input wire  m00_axi_rlast,
		input wire [C_M00_AXI_RUSER_WIDTH-1 : 0] m00_axi_ruser,
		input wire  m00_axi_rvalid,
		output wire  m00_axi_rready,
		input wire rxd,
		output wire txd
	);
	wire temp;
	assign temp = !m00_axi_aresetn;
	Top top (
		.clock(m00_axi_aclk),
  		.reset(temp),
  		.io_mem_axi_0_aw_ready(m00_axi_awready),
  		.io_mem_axi_0_aw_valid(m00_axi_awvalid),
  		.io_mem_axi_0_aw_bits_id(m00_axi_awid),
  		.io_mem_axi_0_aw_bits_addr(m00_axi_awaddr),
  		.io_mem_axi_0_aw_bits_len(m00_axi_awlen),
  		.io_mem_axi_0_aw_bits_size(m00_axi_awsize),
  		.io_mem_axi_0_aw_bits_burst(m00_axi_awburst),
  		.io_mem_axi_0_aw_bits_lock(m00_axi_awlock),
  		.io_mem_axi_0_aw_bits_cache(m00_axi_awcache),
  		.io_mem_axi_0_aw_bits_prot(m00_axi_awprot),
  		.io_mem_axi_0_aw_bits_qos(m00_axi_awqos),
  		.io_mem_axi_0_w_ready(m00_axi_wready),
  		.io_mem_axi_0_w_valid(m00_axi_wvalid),
  		.io_mem_axi_0_w_bits_data(m00_axi_wdata),
  		.io_mem_axi_0_w_bits_strb(m00_axi_wstrb),
  		.io_mem_axi_0_w_bits_last(m00_axi_wlast),
  		.io_mem_axi_0_b_ready(m00_axi_bready),
  		.io_mem_axi_0_b_valid(m00_axi_bvalid),
  		.io_mem_axi_0_b_bits_id(m00_axi_bid),
  		.io_mem_axi_0_b_bits_resp(m00_axi_bresp),
  		.io_mem_axi_0_ar_ready(m00_axi_arready),
  		.io_mem_axi_0_ar_valid(m00_axi_arvalid),
  		.io_mem_axi_0_ar_bits_id(m00_axi_arid),
  		.io_mem_axi_0_ar_bits_addr(m00_axi_araddr),
  		.io_mem_axi_0_ar_bits_len(m00_axi_arlen),
  		.io_mem_axi_0_ar_bits_size(m00_axi_arsize),
  		.io_mem_axi_0_ar_bits_burst(m00_axi_arburst),
  		.io_mem_axi_0_ar_bits_lock(m00_axi_arlock),
  		.io_mem_axi_0_ar_bits_cache(m00_axi_arcache),
  		.io_mem_axi_0_ar_bits_prot(m00_axi_arprot),
  		.io_mem_axi_0_ar_bits_qos(m00_axi_arqos),
  		.io_mem_axi_0_r_ready(m00_axi_rready),
  		.io_mem_axi_0_r_valid(m00_axi_rvalid),
  		.io_mem_axi_0_r_bits_id(m00_axi_rid),
  		.io_mem_axi_0_r_bits_data(m00_axi_rdata),
  		.io_mem_axi_0_r_bits_resp(m00_axi_rresp),
  		.io_mem_axi_0_r_bits_last(m00_axi_rlast),
  		.io_rxd                    (rxd),
  		.io_txd                    (txd)
	);

	// Add user logic here

	// User logic ends

	endmodule
