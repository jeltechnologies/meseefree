# Me See Free

A self-hosted VLESS+REALITY proxy, deployed in minutes with zero networking expertise.

Connect by scanning the QR code from your phone or desktop.

![Portal connection info page](docs/screenshot-connection-info.jpg)
*Sample screenshot with fake data — this connection will not work. Your own portal will show real, working connection details.*

## What it does

Me See Free runs a private VLESS+REALITY proxy on a server you control, disguising your connection as an ordinary visit to a trusted website so it can't be detected or blocked. A web portal generates a unique identity and keypair on first start and gives you a ready-to-scan QR code and connection link — no manual networking configuration required.

## Why

China's Great Firewall and the people routing around it have been locked in a cat-and-mouse game for 25+ years: every time regulators learn to detect a proxy technique, engineers ship the next one. **REALITY** is the current state of the art in that arms race — instead of hiding traffic, it disguises your connection as an ordinary visit to a trusted site (e.g. the regulator's own servers), indistinguishable from millions of other people visiting that same site. There's nothing fake to notice and no unusual pattern to flag — which is why it holds up where earlier VPN protocols get detected and blocked. You never actually connect to or use the camouflage domain — it's disguise only, not a real destination for your traffic.

This isn't just a China problem. The UK's Online Safety Act already requires ISPs and platforms to restrict access by identity and location, and the EU is moving toward similar digital-identity and content-control rules. The playbook is always the same: block specific sites first to "protect the people", then pressure ISPs into blocking the VPNs people use to route around that. Conventional VPNs are easy to fingerprint and blocklist once a regulator decides to go after them. Running your own REALITY proxy now — while it's simple and unregulated — means you own the server and the disguise domain, and aren't dependent on a commercial VPN provider that can be pressured or blocked wholesale.

## What you get

- A cleaner alternative to Tor, which a growing number of people in the UK are starting to use — Tor is also where a lot of shady and illegal activity lives, whereas this is just your own, family-friendly uncensored internet access.
- A compete solutiom, which takes about 15 minutes to deploy, with miminal configuration.
- A private VLESS+REALITY proxy, with a unique identity and keypair generated on first start — nothing shared, nothing hardcoded.
- A web portal (HTTPS, with automatic Let's Encrypt support) with a ready-to-scan QR code and connection link.
- Full ownership: your VPS, your IP, your camouflage domain — no third-party VPN provider to trust or be pressured.

## What you need

- A VPS in an uncensored country (e.g. [Vultr](https://www.vultr.com/) or [DigitalOcean](https://www.digitalocean.com/), US region). Cheapest tier is enough, around **€5/month**.
- Docker installed on that VPS.

The stack generates its own identity, keys, and config automatically on first start.

## Deployment

1. **Source the VPS.** Spin up a small cloud server at a provider of your choice (e.g. Vultr, DigitalOcean), in a jurisdiction without internet censorship. The cheapest tier is enough.

2. **Test the ping.** Before doing anything else, go back to your home connection, switch off any VPN, and ping the VPS's IP address:
   ```
   ping <VPS_IP>
   ```
   If the ping fails or times out, your ISP is likely already blocking that IP range. Go back to your provider, request a new IP (or destroy and recreate the VPS), and test again. If it keeps failing, try a different provider or region entirely. Don't proceed to the next step until this succeeds.

3. **Install Docker** on the VPS.

4. **Download `docker-compose.yaml` and `.env`** into a folder on your VPS:
   ```
   curl -O https://raw.githubusercontent.com/jeltechnologies/meseefree/main/docker-compose.yaml
   curl -o .env https://raw.githubusercontent.com/jeltechnologies/meseefree/main/.env.example
   ```
   `.env` is the file you'll actually edit in the next step — it's gitignored so your real
   credentials never end up in any repo.

5. **Set portal credentials.** Open `.env` and fill in `PORTAL_USER_NAME` and `PORTAL_PASSWORD` —
   they're blank by default, and the portal refuses to show your connection details until both are set.

6. **(Optional) Point a domain at it for a trusted HTTPS certificate.** The portal is HTTPS-only and
   works out of the box with a self-signed certificate. Browsers can't issue a trusted certificate for
   a bare IP address, so if you open the portal via `https://<VPS_IP>:16810` you'll always get a
   warning about the self-signed certificate — this is completely normal and not a sign anything is
   wrong, just click past it. If you'd rather not see that warning at all, point a domain's DNS A/AAAA
   record at your VPS's IP and set `DOMAIN` in `.env`; the stack then automatically obtains and renews
   a real certificate from Let's Encrypt (this needs port 80 reachable from the internet, which the
   stack already publishes for you).

7. **Run it:**
   ```
   docker compose up -d
   ```
   On first start, the stack automatically generates a unique client identity, REALITY keypair, and
   TLS certificate — no manual configuration required beyond the portal credentials above.

8. **Open the portal.** In a browser, go to:
   ```
   https://<VPS_IP>:16810
   ```
   (or `https://<DOMAIN>:16810` if you set one in step 6). Without a configured `DOMAIN`, your browser
   will warn about the self-signed certificate — that's expected and safe to click through, it's just
   how browsers react to any IP-address URL, not a sign of a problem with your setup. Log in with the
   `PORTAL_USER_NAME`/`PORTAL_PASSWORD` you set in step 5.

## How to connect

The portal displays a QR code and connection link for your proxy.

- **Phone (iOS / Android):** use a VLESS+REALITY-compatible client — this has been tested with **V2Box**, available on both the App Store and Google Play. Open the app and scan the QR code shown by the portal.
- **Windows:** use **Hiddify**. Copy the connection link from the portal and paste it into the client.

Other VLESS+REALITY-compatible clients will also work — V2Box and Hiddify are simply the ones this project has been verified against.

### Using v2rayN

v2rayN also works, with one catch: by default it may run your profile on the **sing-box** core, which
has been observed to fail VLESS+REALITY+Vision connections against this stack's Xray-core server with
a `reality verification failed` error on every single request — even when the UUID, public key, short
ID, and server address in the profile are all correct. If you hit this, switch the profile (or the
global default) to the **Xray core** instead — in v2rayN, check the core selection under Settings for
this profile. This resolved the issue in testing; the server-side config needed no changes.

## Credits

Me See Free doesn't implement a proxy protocol of its own — it's a setup and management layer around two existing open source projects:

- **[V2Ray](https://github.com/v2fly/v2ray-core)** (Project V), started in 2015 by a developer known as Victoria Raymond.
- **[Xray-core](https://github.com/XTLS/Xray-core)** (Project X), the actively maintained fork this stack actually runs, created and maintained by **RPRX** — also the author of VLESS, XTLS, and REALITY.

What Me See Free adds on top: automatic identity/keypair generation, HTTPS with Let's Encrypt, a status portal with QR code, and a 15-minute deployment — making Xray-core easy to set up and manage instead of hand-configuring it yourself.

## Disclaimer

This reflects the current state of the art as of July 2026. Censorship is a cat-and-mouse game — this will eventually stop working, and something else will replace it.
