SLIPDecoder {
  // SLIP DECODING
  // ===================
  //
  // The packets are SLIP encoded using these special characters:
  // end = 8r300 (2r11000000 or 0xc0 or 192)
  // esc = 8r333 (2r11011011 or 0xdb or 219)
  // esc_end = 8r334 (2r011011100 or 0xdc or 220)
  // esc_esc = 8r335 (2r011011101 or 0xdd or 221)
  // original code by Martin Marier & Fredrik Olofsson
  const slipEND = 0xC0;
  const slipESC = 0xDB;
  const slipESC_END = 0xDC;
  const slipESC_ESC = 0xDD;

  // OSC DECODING
  // ============
  // bundle header "#bundle"
  // type separator ","
  const oscBundleHeader = #[35, 98, 117, 110, 100, 108, 101, 0];
  const oscTypeSeparator = 0x2C;

  // Buffer for OSC bundles/messages
  const bufferSize = 4096;

  var <>deviceName, <>rate, <>prepend, <port, <>trace = false, <running = false, <reader;

  *new { |deviceName="/dev/ttyUSB0", rate=115200, prepend=""|
    ^super.newCopyArgs(deviceName, rate, prepend);
  }

  readInt32 { |byteArr|
    ^((byteArr[0] << 24) + (byteArr[1] << 16) + (byteArr[2] << 8) + byteArr[3]);
  }

  readFloat32 { |byteArr|
    ^Float.from32Bits(this.readInt32(byteArr));
  }

  // Read byte array 4 bytes at a time, return on null termination or if end of buffer reached
  // Return array [string, strlen (bytes)]
  readNextString { |byteArr|
    var str = "", idx = 0;
    while({ (idx + 4) <= byteArr.size }, {
      str = str ++ String.newFrom(
        byteArr[(idx..(idx + 3))]
          .removeEvery([0, nil])
          .collect({|x| x.asAscii})
      );

      // If last byte is null, return
      if ( isNil(byteArr[idx + 3]) || (byteArr[idx + 3] == 0), {
        ^[str, idx + 4]
      });
      idx = idx + 4;
    });

    // If end of byteArr, return
    ^[str, idx];
  }

  int2chr { |x|
    // ce je stevilo, v razponu crk, stevk in znakov, vrni char, sicer kar cifro
    if (x.isInteger && (x >= 0x20) && (x <= 0x7E),
      { ^x.asAscii },
      { ^x }
    );
  }

  init {
    port = SerialPort(deviceName, rate);
  }

  traceMsg { |...msg|
    if ((trace), { "> ".post; msg.join(' ').postln; });
  }

  // Function for decoding the properly-SLIP-decoded message.
  decode { |data|
    var header = data.at((0..7));

    // Is it a bundle? (read header from first 8 bytes)
    if ((header == oscBundleHeader), {
      // OSC bundle
      var nextMsgLen, nextMsgStart, nextMsgEnd, bundlePart;
      this.traceMsg("BUNDLE");

      // Next 8 bytes are a time tag (or null)
      /* @TODO handle timetags!
      var timetag = data.at((8..15));
      */

      // First message starts after the time tag
      nextMsgStart = 16;

      // Loop for each message
      while({ nextMsgStart < data.size }, {
        // Further 4 bytes hold the next message length
        nextMsgLen = this.readInt32(data.at((nextMsgStart..(nextMsgStart + 3))));
        nextMsgStart = nextMsgStart + 4;
        nextMsgEnd = min(nextMsgStart + nextMsgLen - 1, data.size - 1);
        bundlePart = data.at((nextMsgStart..nextMsgEnd));

        // Recursively decode; each bundle part can be another bundle
        this.decode(bundlePart);

        nextMsgStart = nextMsgEnd + 1;
      });
    }, {
      // OSC message
      var nextString, index, address, type, args = [];

      // Message Address
      nextString = this.readNextString(data);
      address = nextString[0];
      data = data[(nextString[1]..(data.size - 1))];

      nextString = this.readNextString(data);
      type = nextString[0];
      data = data[(nextString[1]..(data.size - 1))];

      type.do({ |t|
        t.switch (
          $i, {
            args = args.add(this.readInt32(data));
            data = data[(4..(data.size - 1))];
          },
          $f, {
            args = args.add(this.readFloat32(data));
            data = data[(4..(data.size - 1))];
          },
          $s, {
            nextString = this.readNextString(data);
            args = args.add(nextString[0]);
            data = data[(nextString[1]..(data.size - 1))];
          }
          /* @TODO implement strings, bytearrays */
        );
      });
      this.traceMsg("OSC", prepend ++ address, args);

      // Send OSC message to the engine
      NetAddr.localAddr.sendMsg(prepend ++ address, *args);
    });
  }

  start {
    this.traceMsg("Starting...");
    if ((port == nil), {this.init;});
    if (port.isOpen.not, {this.init;});

    this.traceMsg("opened");
    running = true;

    reader = fork {
      var serialByte, buffer, firstRead;
      firstRead = true;

      // Skip data before the first END character
      while({running && firstRead}, {
        serialByte = port.read;

        //this.traceMsg("Read byte");
        //this.traceMsg(serialByte.asAscii);

        if (serialByte == slipEND, {
          buffer = Int8Array(maxSize: bufferSize);
          firstRead = false;
        }, {
          this.traceMsg("Skip...")
        });
      });

      // Start reading data
      this.traceMsg("First read!");
      while({running}, {
        serialByte = port.read;
        //this.traceMsg("Checking", serialByte.asInteger, serialByte.asAscii);
        serialByte.switch(
        // on END, decode buffer
        slipEND, {
          //this.traceMsg("SLIP END, decoding ");
          //this.traceMsg(buffer);
          //3.wait;
          if (buffer.isEmpty.not, {
            this.traceMsg("decode!", buffer);
            this.decode(buffer);
            buffer = Int8Array(maxSize: bufferSize);
          });
        },
        slipESC, {
          serialByte = port.read;
          serialByte.switch(
            slipESC_END, { buffer.add(slipEND) },
            slipESC_ESC, { buffer.add(slipESC) },
            {"SLIP encoding error.".error }
          );
        }, {
          // Otherwise just add the byte
          //this.traceMsg(buffer);
          buffer.add(serialByte);
        });
      });
    };
  }

  stop {
    this.traceMsg("Stopping...");
    running = false;
    port.close;
    this.reader.free;
  }
}
