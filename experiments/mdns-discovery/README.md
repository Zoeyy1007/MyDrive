# MyDrive mDNS experiment

This is deliberately separate from the Spring server and sync client. It tests only one
question: can one computer advertise `_mydrive._tcp.local.` and can another computer on
the same LAN resolve its IP address and port?

## Before running it

- Install Java 21 and Maven on both computers. You can also use this repository's Maven
  wrapper after copying/cloning the whole repository to each computer.
- Put Windows and the Mac on the same normal Wi-Fi network. Guest Wi-Fi often blocks
  device-to-device traffic.
- Temporarily disconnect VPNs because they often become Java's selected interface.
- When Windows or macOS asks whether Java may accept network traffic, allow it on your
  private/local network.
- This experiment advertises port 8080, but it does not start an HTTP server. That is
  enough for testing mDNS discovery.

## 1. Find each computer's Wi-Fi address

Windows PowerShell:

```powershell
ipconfig
```

Look under the active Wi-Fi adapter for an IPv4 address such as `192.168.1.32`.

macOS Terminal:

```bash
ipconfig getifaddr en0
```

If that prints nothing, open System Settings > Wi-Fi > Details > TCP/IP and copy the
IPv4 address.

## 2. Load it in IntelliJ once

Open `experiments/mdns-discovery/pom.xml` in IntelliJ and choose **Load Maven Project**.
You can then open either main class and use the green Run arrow. Put the arguments in
the Run Configuration's **Program arguments** field. This is the easiest Windows option
if the `mvn` command is not installed globally.

## 3. Start the advertiser on Windows

From the repository root, replace `192.168.1.32` with the Windows Wi-Fi address:

```powershell
.\mvnw.cmd -f experiments\mdns-discovery\pom.xml compile exec:java "-Dexec.mainClass=com.mydrive.experiment.mdns.MyDriveAdvertiser" "-Dexec.args=192.168.1.32 8080"
```

If the Maven wrapper is unavailable, use `mvn` instead of `.\mvnw.cmd`.

Leave this terminal open. The advertiser stops when you press Enter.

## 4. Start the finder on the Mac

From the repository root, replace `192.168.1.45` with the Mac Wi-Fi address:

```bash
./mvnw -f experiments/mdns-discovery/pom.xml compile exec:java \
  -Dexec.mainClass=com.mydrive.experiment.mdns.MyDriveFinder \
  -Dexec.args="192.168.1.45 15"
```

Or use `mvn` instead of `./mvnw`. A successful result looks like:

```text
Found MyDrive!
192.168.1.32:8080
```

You can reverse the programs too: advertise on the Mac and find from Windows.

## If automatic address selection works

The explicit address is optional. These shorter commands let the experiment choose an
active multicast-capable IPv4 interface:

```powershell
.\mvnw.cmd -f experiments\mdns-discovery\pom.xml compile exec:java "-Dexec.mainClass=com.mydrive.experiment.mdns.MyDriveAdvertiser"
```

```bash
./mvnw -f experiments/mdns-discovery/pom.xml compile exec:java \
  -Dexec.mainClass=com.mydrive.experiment.mdns.MyDriveFinder
```

Pass the explicit Wi-Fi address if the printed `Using LAN interface` address belongs to
Docker, a virtual machine, Ethernet you are not using, or a VPN.

## If nothing is found

1. Confirm the two IP addresses begin with the same LAN prefix, commonly `192.168.1`.
2. Confirm the advertiser is still running.
3. Disable VPNs on both devices.
4. Ensure Windows marks the Wi-Fi as a Private network and Java is allowed through the
   Windows Defender Firewall for Private networks.
5. Check that the router does not enable AP/client isolation.
6. UDP port 5353 is mDNS. TCP port 8080 matters only later when testing the real API.

Passing this experiment proves LAN advertisement and browsing work. It does not yet
prove that `/api/discovery/info`, login, upload, or synchronization work.
