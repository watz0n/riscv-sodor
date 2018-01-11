#include "dtmxsdb.h"
#include "debug_defines.h"
#include "encoding.h"
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <assert.h>
#include <pthread.h>
#include <errno.h>
#include <fcntl.h> 
#include <termios.h>
#include <unistd.h>

#define RV_X(x, s, n) \
  (((x) >> (s)) & ((1 << (n)) - 1))
#define ENCODE_ITYPE_IMM(x) \
  (RV_X(x, 0, 12) << 20)
#define ENCODE_STYPE_IMM(x) \
  ((RV_X(x, 0, 5) << 7) | (RV_X(x, 5, 7) << 25))
#define ENCODE_SBTYPE_IMM(x) \
  ((RV_X(x, 1, 4) << 8) | (RV_X(x, 5, 6) << 25) | (RV_X(x, 11, 1) << 7) | (RV_X(x, 12, 1) << 31))
#define ENCODE_UTYPE_IMM(x) \
  (RV_X(x, 12, 20) << 12)
#define ENCODE_UJTYPE_IMM(x) \
  ((RV_X(x, 1, 10) << 21) | (RV_X(x, 11, 1) << 20) | (RV_X(x, 12, 8) << 12) | (RV_X(x, 20, 1) << 31))

#define LOAD(xlen, dst, base, imm) \
  (((xlen) == 64 ? 0x00003003 : 0x00002003) \
   | ((dst) << 7) | ((base) << 15) | (uint32_t)ENCODE_ITYPE_IMM(imm))
#define STORE(xlen, src, base, imm) \
  (((xlen) == 64 ? 0x00003023 : 0x00002023) \
   | ((src) << 20) | ((base) << 15) | (uint32_t)ENCODE_STYPE_IMM(imm))
#define JUMP(there, here) (0x6f | (uint32_t)ENCODE_UJTYPE_IMM((there) - (here)))
#define BNE(r1, r2, there, here) (0x1063 | ((r1) << 15) | ((r2) << 20) | (uint32_t)ENCODE_SBTYPE_IMM((there) - (here)))
#define ADDI(dst, src, imm) (0x13 | ((dst) << 7) | ((src) << 15) | (uint32_t)ENCODE_ITYPE_IMM(imm))
#define SRL(dst, src, sh) (0x5033 | ((dst) << 7) | ((src) << 15) | ((sh) << 20))
#define FENCE_I 0x100f
#define EBREAK  0x00100073
#define X0 0
#define S0 8
#define S1 9

#define AC_AR_REGNO(x) ((0x1000 | x) << AC_ACCESS_REGISTER_REGNO_OFFSET)
#define AC_AR_SIZE(x)  (((x == 128)? 4 : (x == 64 ? 3 : 2)) << AC_ACCESS_REGISTER_SIZE_OFFSET)

#define WRITE 1
#define SET 2
#define CLEAR 3
#define CSRRx(type, dst, csr, src) (0x73 | ((type) << 12) | ((dst) << 7) | ((src) << 15) | (uint32_t)((csr) << 20))

#define get_field(reg, mask) (((reg) & (mask)) / ((mask) & ~((mask) << 1)))
#define set_field(reg, mask, val) (((reg) & ~(mask)) | (((val) * ((mask) & ~((mask) << 1))) & (mask)))

#define RUN_AC_OR_DIE(a, b, c, d, e) { \
    uint32_t cmderr = run_abstract_command(a, b, c, d, e);      \
    if (cmderr) {                                               \
      die(cmderr);                                              \
    }                                                           \
  }

bool log = false;
char *port;

uint32_t dtmxsdb_t::do_command(dtmxsdb_t::req r)
{
  // if data/header contains 0xfe then repeat it 
  // to avoid being interpreted as byte for flow control
  unsigned int n,data,i = 0;
  if (r.op == 2) {
    message[i] = 128 + r.addr;
    if (message[i] == 254)
      message[++i] = 254;
    message[i + 1] = (r.data & 0xff000000) >> 24;
    if ((r.data & 0xff000000) == 0xfe000000)
      message[++i + 1] = 254;
    message[i + 2] = (r.data & 0x00ff0000) >> 16;
    if ((r.data & 0x00ff0000) == 0x00fe0000) 
      message[++i + 2] = 254;
    message[i + 3] = (r.data & 0x0000ff00) >> 8;
    if ((r.data & 0x0000ff00) == 0x0000fe00) 
      message[++i + 3] = 254;
    message[i + 4] = r.data & 0x000000ff;
    if ((r.data & 0x000000ff) == 0x000000fe) 
      message[++i + 4] = 254;
    if( ::write(sock , message , i+5) < 0)
    {
      puts("Send failed");
      exit(1);
    }
  }
  else {
    message[0] = r.addr;
    if( ::write(sock , message , 1) < 0)
    {
      puts("Send failed");
      exit(1);
    }
  }
  if(log)
    printf("O:%x A:%hhx D:%hhx%hhx%hhx%hhx\n",r.op,message[0] & 0x7f,message[1],message[2],message[3],message[4]);
  
  // Process Response
  i = 1;
  data = 0;
  n = ::read(sock , response , 10);
  if (n == 2)
    return 0;
  else {
    data |= ((response[i] << 24) & 0xff000000);
    if ((uint8_t)response[i] == 0xfe)  i++;
    data |= ((response[i + 1] << 16) & 0x00ff0000);
    if ((uint8_t)response[i + 1] == 0xfe)  i++;
    data |= ((response[i + 2] << 8) & 0x0000ff00);
    if ((uint8_t)response[i + 2] == 0xfe)  i++;
    data |= response[i + 3] & 0x000000ff;
    if (log)
      printf("Resp: %x\n", data);
    return data;
  }
}

uint32_t dtmxsdb_t::read(uint32_t addr)
{
  return do_command((req){addr, 1, 0});
}

uint32_t dtmxsdb_t::write(uint32_t addr, uint32_t data)
{
  do_command((req){addr, 2, data});
  return 0;
}

void dtmxsdb_t::nop()
{
  do_command((req){0, 0, 0});
}

void dtmxsdb_t::halt()
{
  write(DMI_DMCONTROL, DMI_DMCONTROL_HALTREQ | DMI_DMCONTROL_DMACTIVE);
  while(get_field(read(DMI_DMSTATUS), DMI_DMSTATUS_ALLHALTED) == 0);
}

void dtmxsdb_t::resume()
{
  write(DMI_DMCONTROL, DMI_DMCONTROL_RESUMEREQ | DMI_DMCONTROL_DMACTIVE);
  while (get_field(read(DMI_DMSTATUS), DMI_DMSTATUS_ALLRUNNING) == 0); 
}

uint64_t dtmxsdb_t::save_reg(unsigned regno)
{
  uint32_t data[xlen/(8*4)];
  uint32_t command = AC_ACCESS_REGISTER_TRANSFER | AC_AR_SIZE(xlen) | AC_AR_REGNO(regno);
  RUN_AC_OR_DIE(command, 0, 0, data, xlen / (8*4));

  uint64_t result = data[0];
  if (xlen > 32) {
    result |= ((uint64_t)data[1]) << 32;
  }
  return result;
}

void dtmxsdb_t::restore_reg(unsigned regno, uint64_t val)
{
  uint32_t data[xlen/(8*4)];
  data[0] = (uint32_t) val;
  if (xlen > 32) {
    data[1] = (uint32_t) (val >> 32);
  }

  uint32_t command = AC_ACCESS_REGISTER_TRANSFER |
    AC_ACCESS_REGISTER_WRITE |
    AC_AR_SIZE(xlen) |
    AC_AR_REGNO(regno);
  
  RUN_AC_OR_DIE(command, 0, 0, data, xlen / (8*4));

}

uint32_t dtmxsdb_t::run_abstract_command(uint32_t command,
                                     const uint32_t program[], size_t program_n,
                                     uint32_t data[], size_t data_n)
{ 
  assert(program_n <= ram_words);
  assert(data_n    <= data_words);
  
  for (size_t i = 0; i < program_n; i++) {
    write(DMI_PROGBUF0 + i, program[i]);
  }

  if (get_field(command, AC_ACCESS_REGISTER_WRITE) &&
      get_field(command, AC_ACCESS_REGISTER_TRANSFER)) {
    for (size_t i = 0; i < data_n; i++) {
      write(DMI_DATA0 + i, data[i]);
    }
  }
  
  write(DMI_COMMAND, command);
  
  // Wait for not busy and then check for error.
  uint32_t abstractcs;
  do {
    abstractcs = read(DMI_ABSTRACTCS);
  } while (abstractcs & DMI_ABSTRACTCS_BUSY);

  if ((get_field(command, AC_ACCESS_REGISTER_WRITE) == 0) &&
      get_field(command, AC_ACCESS_REGISTER_TRANSFER)) {
    for (size_t i = 0; i < data_n; i++){
      data[i] = read(DMI_DATA0 + i);
    }
  }
  
  return get_field(abstractcs, DMI_ABSTRACTCS_CMDERR);

}

size_t dtmxsdb_t::chunk_align()
{
  return xlen / 8;
}

void dtmxsdb_t::read_chunk(uint64_t taddr, size_t len, void* dst)
{
  //uint32_t prog[ram_words];
  //uint32_t data[data_words];

  uint32_t * curr = (uint32_t*) dst;
  taddr = (taddr & 0x1fffff) | 0x10000000;
  //halt();
  write(DMI_SBCS,DMI_SBCS_SBAUTOINCREMENT);
  write(DMI_SBADDRESS0,taddr);
  for (size_t i = 0; i < (len * 8 / xlen); i++){
    curr[i] = read(DMI_SBDATA0);
  }  
  write(DMI_SBCS,0);
//  memcpy(data, read(DMI_SBDATA0), xlen/8);
  //resume(); 

}

void dtmxsdb_t::write_chunk(uint64_t taddr, size_t len, const void* src)
{  
/*  uint32_t prog[ram_words];*/
  uint32_t data[data_words];

  const uint8_t * curr = (const uint8_t*) src;
  taddr = (taddr & 0x1fffff) | 0x10000000;
  //halt();
  if (src != 0)
    memcpy(data, curr, xlen/8);
  else 
    data[0] = 0;
  write(DMI_SBCS,DMI_SBCS_SBAUTOINCREMENT);
  write(DMI_SBADDRESS0,taddr);
  write(DMI_SBDATA0,data[0]);
  for (size_t i = 1; i < (len * 8 / xlen); i++){
    curr += xlen/8;
    if (src != 0)
      memcpy(data, curr, xlen/8);
    else
      data[0] = 0;
    write(DMI_SBDATA0,data[0]);
  }
  //resume();
}

void dtmxsdb_t::die(uint32_t cmderr)
{
  const char * codes[] = {
    "OK",
    "BUSY",
    "NOT_SUPPORTED",
    "EXCEPTION",
    "HALT/RESUME"
  };
  const char * msg;
  if (cmderr < (sizeof(codes) / sizeof(*codes))){
    msg = codes[cmderr];
  } else {
    msg = "OTHER";
  }
  throw std::runtime_error("Debug Abstract Command Error #" + std::to_string(cmderr) + "(" +  msg + ")");
}

void dtmxsdb_t::clear_chunk(uint64_t taddr, size_t len)
{
  write_chunk(taddr,len,0);
/*  uint32_t prog[ram_words];
  uint32_t data[data_words];
  
  halt();
  uint64_t s0 = save_reg(S0);
  uint64_t s1 = save_reg(S1);

  uint32_t command;

  // S0 = Addr
  data[0] = (uint32_t) taddr;
  data[1] = (uint32_t) (taddr >> 32);
  command = AC_ACCESS_REGISTER_TRANSFER |
    AC_ACCESS_REGISTER_WRITE |
    AC_AR_SIZE(xlen) |
    AC_AR_REGNO(S0);
  RUN_AC_OR_DIE(command, 0, 0, data, xlen/(4*8));

  // S1 = Addr + len, loop until S0 = S1
  prog[0] = STORE(xlen, X0, S0, 0);
  prog[1] = ADDI(S0, S0, xlen/8);
  prog[2] = BNE(S0, S1, 0*4, 2*4);
  prog[3] = EBREAK;

  data[0] = (uint32_t) (taddr + len);
  data[1] = (uint32_t) ((taddr + len) >> 32);
  command = AC_ACCESS_REGISTER_TRANSFER |
    AC_ACCESS_REGISTER_WRITE |
    AC_AR_SIZE(xlen) |
    AC_AR_REGNO(S1)  |
    AC_ACCESS_REGISTER_POSTEXEC;
  RUN_AC_OR_DIE(command, prog, 4, data, xlen/(4*8));

  restore_reg(S0, s0);
  restore_reg(S1, s1);

  resume();*/
}

uint64_t dtmxsdb_t::write_csr(unsigned which, uint64_t data)
{
  return modify_csr(which, data, WRITE);
}

uint64_t dtmxsdb_t::set_csr(unsigned which, uint64_t data)
{
  return modify_csr(which, data, SET);
}

uint64_t dtmxsdb_t::clear_csr(unsigned which, uint64_t data)
{
  return modify_csr(which, data, CLEAR);
}

uint64_t dtmxsdb_t::read_csr(unsigned which)
{
  return set_csr(which, 0);
}

uint64_t dtmxsdb_t::modify_csr(unsigned which, uint64_t data, uint32_t type)
{
  halt();
  // This code just uses DSCRATCH to save S0
  // and data_base to do the transfer so we don't
  // need to run more commands to save and restore
  // S0.
  uint32_t prog[] = {
    CSRRx(WRITE, S0, CSR_DSCRATCH, S0),
    LOAD(xlen, S0, X0, data_base),
    CSRRx(type, S0, which, S0),
    STORE(xlen, S0, X0, data_base),
    CSRRx(WRITE, S0, CSR_DSCRATCH, S0),
    EBREAK
  };

  //TODO: Use transfer = 0. For now both HW and OpenOCD
  // ignore transfer bit, so use "store to X0" NOOP.
  // We sort of need this anyway because run_abstract_command
  // needs the DATA to be written so may as well use the WRITE flag.
  
  uint32_t adata[] = {(uint32_t) data,
                      (uint32_t) (data >> 32)};
  
  uint32_t command = AC_ACCESS_REGISTER_POSTEXEC |
    AC_ACCESS_REGISTER_TRANSFER |
    AC_ACCESS_REGISTER_WRITE |
    AC_AR_SIZE(xlen) |
    AC_AR_REGNO(X0);
  
  RUN_AC_OR_DIE(command, prog, sizeof(prog) / sizeof(*prog), adata, xlen/(4*8));
  
  uint64_t res = read(DMI_DATA0);//adata[0];
  if (xlen == 64)
    res |= read(DMI_DATA1);//((uint64_t) adata[1]) << 32;
  
  resume();
  return res;  
}

size_t dtmxsdb_t::chunk_max_size()
{
  // Arbitrary choice. 4k Page size seems reasonable.
  return 4096;
}

uint32_t dtmxsdb_t::get_xlen()
{
  // Attempt to read S0 to find out what size it is.
  // You could also attempt to run code, but you need to save registers
  // to do that anyway. If what you really want to do is figure out
  // the size of S0 so you can save it later, then do that.
  uint32_t command = AC_ACCESS_REGISTER_TRANSFER | AC_AR_REGNO(S0);
  uint32_t cmderr;
  
  const uint32_t prog[] = {};
  uint32_t data[] = {};

  cmderr = run_abstract_command(command | AC_AR_SIZE(128), prog, 0, data, 0);
  if (cmderr == 0){
    throw std::runtime_error("FESVR DTM Does not support 128-bit");
    abort();
    return 128;
  }
  write(DMI_ABSTRACTCS, DMI_ABSTRACTCS_CMDERR);

  cmderr = run_abstract_command(command | AC_AR_SIZE(64), prog, 0, data, 0);
  cmderr = run_abstract_command(command | AC_AR_SIZE(64), prog, 0, data, 0);
  if (cmderr == 0){
    return 64;
  }
  write(DMI_ABSTRACTCS, DMI_ABSTRACTCS_CMDERR);

  cmderr = run_abstract_command(command | AC_AR_SIZE(32), prog, 0, data, 0);
  cmderr = run_abstract_command(command | AC_AR_SIZE(32), prog, 0, data, 0);
  if (cmderr == 0){
    return 32;
  }
  
  throw std::runtime_error("FESVR DTM can't determine XLEN. Aborting");
}

void dtmxsdb_t::fence_i()
{
  halt();

  const uint32_t prog[] = {
    FENCE_I,
    EBREAK
  };

  //TODO: Use the transfer = 0.
  uint32_t command = AC_ACCESS_REGISTER_POSTEXEC |
    AC_ACCESS_REGISTER_TRANSFER |
    AC_ACCESS_REGISTER_WRITE |
    AC_AR_SIZE(xlen) |
    AC_AR_REGNO(X0);

  RUN_AC_OR_DIE(command, prog, sizeof(prog)/sizeof(*prog), 0, 0);
  
  resume();

}

void host_thread_main(void* arg)
{
  ((dtmxsdb_t*)arg)->producer_thread();
}

void dtmxsdb_t::reset()
{
  // Each of these functions already
  // does a halt and resume.
  fence_i();
  write(68,10);
  //write_csr(0x7b1, get_entry_point());
}

void dtmxsdb_t::idle()
{
  for (int idle_cycles = 0; idle_cycles < max_idle_cycles; idle_cycles++)
    nop();
}

void dtmxsdb_t::producer_thread()
{
  // Learn about the Debug Module and assert things we
  // depend on in this code.

  // These are checked every time we run an abstract command.
  uint32_t abstractcs = read(DMI_ABSTRACTCS);
  ram_words = get_field(abstractcs, DMI_ABSTRACTCS_PROGSIZE);
  data_words = get_field(abstractcs, DMI_ABSTRACTCS_DATACOUNT);

  // These things are only needed for the 'modify_csr' function.
  // That could be re-written to not use these at some performance
  // overhead.
  uint32_t hartinfo = read(DMI_HARTINFO);
  assert(get_field(hartinfo, DMI_HARTINFO_NSCRATCH) > 0);
  assert(get_field(hartinfo, DMI_HARTINFO_DATAACCESS));

  data_base = get_field(hartinfo, DMI_HARTINFO_DATAADDR);

  // Enable the debugger.
  write(DMI_DMCONTROL, DMI_DMCONTROL_DMACTIVE);
  
  halt();
  xlen = get_xlen();
  resume();
  int exit_code = htif_t::run();
  if(exit_code != 0){
    do_command((req){0x48, 2, 1});
    exit(1);
  }
  else {
    do_command((req){0x48, 2, 1});
    printf("Sucess\n");
    exit(0);
  }
}

int set_interface_attribs (int fd, int speed, int parity)
{
    struct termios tty;
    memset (&tty, 0, sizeof tty);
    if (tcgetattr (fd, &tty) != 0)
    {
            fprintf(stderr,"error %d from tcgetattr\n", errno);
            return -1;
    }

        cfsetospeed (&tty, speed);
        cfsetispeed (&tty, speed);

        tty.c_cflag = (tty.c_cflag & ~CSIZE) | CS8;     // 8-bit chars
        // disable IGNBRK for mismatched speed tests; otherwise receive break
        // as \000 chars
        tty.c_iflag &= ~IGNBRK;         // disable break processing
        tty.c_lflag = 0;                // no signaling chars, no echo,
                                        // no canonical processing
        tty.c_oflag = 0;                // no remapping, no delays
        tty.c_cc[VMIN]  = 0;            // read doesn't block
        tty.c_cc[VTIME] = 5;            // 0.5 seconds read timeout

        tty.c_iflag &= ~(IXON | IXOFF | IXANY); // shut off xon/xoff ctrl

        tty.c_cflag |= (CLOCAL | CREAD);// ignore modem controls,
                                        // enable reading
        tty.c_cflag &= ~(PARENB | PARODD);      // shut off parity
        tty.c_cflag |= parity;
        tty.c_cflag &= ~CSTOPB;
        tty.c_cflag &= ~CRTSCTS;

        if (tcsetattr (fd, TCSANOW, &tty) != 0)
        {
                fprintf(stderr,"error %d from tcsetattr\n", errno);
                return -1;
        }
        return 0;
}

void set_blocking (int fd, int should_block)
{
    struct termios tty;
    memset (&tty, 0, sizeof tty);
    if (tcgetattr (fd, &tty) != 0)
    {
            fprintf(stderr,"error %d from tggetattr\n", errno);
            exit(1);
    }

    tty.c_cc[VMIN]  = should_block ? 2 : 0;
    tty.c_cc[VTIME] = 5;            // 0.5 seconds read timeout

    if (tcsetattr (fd, TCSANOW, &tty) != 0)
        fprintf(stderr,"error %d setting term attributes\n", errno);
}

void dtmxsdb_t::start_host_thread()
{

  sock = open (port, O_RDWR | O_NOCTTY | O_SYNC);
  if (sock < 0)
  {
    fprintf(stderr,"error %d opening %s: %s\n", errno, port, strerror (errno));
    exit(1);
  }

  set_interface_attribs (sock, B115200, 0);  // set speed to 115,200 bps, 8n1 (no parity)
  set_blocking (sock, 1);                // set no blocking
  //target = context_t::current();
  host.init(host_thread_main, this);
  host.switch_to();
  //host.switch_to();
}

dtmxsdb_t::dtmxsdb_t(const std::vector<std::string>& args)
  : htif_t(args)
{
  start_host_thread();
}

dtmxsdb_t::~dtmxsdb_t()
{
}

int main(int argc, char** argv)
{
  uint64_t max_cycles = 0;
  const char* loadmem = NULL;
  const char* failure = NULL;
  std::vector<std::string> to_dtm;
  for (int i = 1; i < argc; i++)
  {
    std::string arg = argv[i];
    if (arg.substr(0, 2) == "+p")
      port = argv[i]+2;
    else if (arg == "+verbose")
      log = true;
    else if (arg.substr(0, 12) == "+max-cycles=")
      max_cycles = atoll(argv[i]+12);
    else if (arg.substr(0, 9) == "+loadmem="){
      loadmem = argv[i]+9;
      to_dtm.push_back(argv[i]+9);
    }
  }

  dtmxsdb_t *dtm = new dtmxsdb_t(to_dtm);
}



