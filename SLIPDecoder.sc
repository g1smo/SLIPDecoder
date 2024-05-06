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

	// OSC bundle header "#bundle"
	const oscBundleHeader = #[35, 98, 117, 110, 100, 108, 101, 0];

	// OSC type separator ","
	const oscTypeSeparator = 0x2C;

	var deviceName, <>prependAddress, <>rate, <>port, decode, <>actions, firstRead, trace, stop, <>reader;

	*new {|deviceName="/dev/ttyUSB0", rate=9600, prepend=""|
		^super.new.initSLIPDecoder(deviceName, rate, prepend);
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
		while({ (idx + 4) <= byteArr.size },
			{
				str = str ++ String.newFrom(
					byteArr[(idx..(idx + 3))]
					    .removeEvery([0])
					    .collect({|x| x.asAscii})
				);

				// If last byte is null, return
				if ( isNil(byteArr[idx + 3]) || (byteArr[idx + 3] == 0), {
					^[str, idx + 4]
				});

				idx = idx + 4;
			}
		);

		// If end of byteArr, return
		^[str, idx];
	}

	int2chr { |x|
		// Ce ni stevilo, vrni isto
		if ((x.isInteger.not), { ^x });
		// v razponu crk, stevilk in znakov vrni char, sicer kar cifro
		if (x.isInteger && (x >= 0x20) && (x <= 0x7E),
			{ ^x.asAscii },
			{ ^x })
	}

	initSLIPDecoder { |deviceName,rate,prepend|
		port = SerialPort(deviceName, rate);
		prependAddress = prepend;
		actions = []; // Each action is a function that takes the message contents as an argument. see 'decode' below. 
		firstRead = false;
		trace = false;
		stop = false;
	}

	trace { |val|
		trace = val;
	}

	// Function for decoding the properly-SLIP-decoded message.
	decode { |data| 
		// Is it a bundle? (read header from first 8 bytes)
		var header = data.at((0..7));

		if (header == oscBundleHeader,
			// OSC bundle
			{
				var timetag, nextMsgLen, nextMsgStart, nextMsgEnd, bundlePart;

				if (trace, {
					"".postln;
					"======".postln;
					"BUNDLE".postln;
					"======".postln;
				});

				// Next 8 bytes are a time tag (or null)
				/* @TODO handle timetags!
				timetag = data.at((8..15));
				*/

				// First message starts after the time tag
				nextMsgStart = 16;

				// Loop for each message
				while({ nextMsgStart < data.size },
					{
						// Further 4 bytes hold the next message length
						nextMsgLen = this.readInt32(data.at((nextMsgStart..(nextMsgStart + 3))));
						nextMsgStart = nextMsgStart + 4;
						nextMsgEnd = nextMsgStart + nextMsgLen - 1;
						bundlePart = data.at((nextMsgStart..nextMsgEnd));

						// Recursively decode; each bundle part can be another bundle
						this.decode(bundlePart);

						nextMsgStart = nextMsgEnd + 1;
					}
				);
			},
			// OSC message
			{
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

				// Send OSC message to the engine
				NetAddr.localAddr.sendMsg(prependAddress ++ address, *args);
			}
		);
	}

	start {
		stop = false;
		reader = fork {
			var buffer, serialByte;

			var bufferSize = 1024; // 1KB
			buffer = Int8Array(maxSize: bufferSize);
			while({stop.not}, {
				serialByte = port.read;
				serialByte.switch(
					slipEND, {
						if (firstRead && buffer.isEmpty.not,
							{
								this.decode(buffer);
							},
							{ firstRead = true; }
						);
						buffer = Int8Array(maxSize: bufferSize);
					},
					slipESC, {
						serialByte = port.read;
						serialByte.switch(
							slipESC_END, { buffer.add(slipEND) },
							slipESC_ESC, { buffer.add(slipESC) },
							{"SLIP encoding error.".error }
						)
					},
					{ buffer.add(serialByte) }
				);
			});
		};
	}

	stop { reader.stop }
}
