import os
import re
from io import BytesIO
from typing import Optional

import bottle
from bottle import response

import requests
from PIL import Image
from PIL import ImageDraw
from PIL import ImageFont
from PIL import ImageFilter


def kebabcase(s):
    return "-".join(re.findall(
        r"[A-Z]{2,}(?=[A-Z][a-z]+[0-9]*|\b)|[A-Z]?[a-z]+[0-9]*|[A-Z]|[0-9]+",
        s.lower()
    ))


class Beaches:
    def __init__(self, base_uri):
        self.base_uri = base_uri

    def totals_for(self, beach_slug) -> Optional[dict]:
        uri = f"{self.base_uri}/v1/2022/spills-by-beach.json"

        response = requests.get(uri)
        response.raise_for_status()
        data = response.json()

        def matching(d):
            return d["reporting_year"] == 2022 and kebabcase(d["bathing"]) == beach_slug

        wanted = [d for d in data if matching(d)]

        if len(wanted):
            return wanted[0]
        else:
            return None


class Badges:
    def __init__(self):
        self.big_font = ImageFont.truetype(font="Roboto-Medium.ttf", size=48)
        self.med_font = self.big_font.font_variant(size=32)
        self.small_font = self.big_font.font_variant(size=16)

    def create_beach_badge(self, beach, spills, company, year) -> Image.Image:
        background = Image.open("assets/beach.jpg").filter(ImageFilter.GaussianBlur(3))
        poop = Image.open("assets/poop.png")
        poop = poop.resize(size=(128, 128))

        image = Image.new("RGBA", (526, 263), (0, 0, 0, 0))

        image.paste(background)
        image.alpha_composite(poop, dest=(400, 120))

        draw = ImageDraw.Draw(image)

        def text(**kwargs):
            draw.text(fill=(255, 255, 255), stroke_fill=(0, 0, 0), stroke_width=2, **kwargs)

        text(xy=(10, 25 + 0), text=f"{beach}", font=self.big_font)
        text(xy=(10, 25 + 64), text=f"had sewage dumped on it\n{spills:,d} times\nby {company} in {year}", font=self.med_font)
        text(xy=(10, 230), text="top-of-the-poops.org", font=self.small_font)

        return image


class BeachBadges:

    def __init__(self, app, beaches: Beaches, badges: Badges):
        self.app = app
        self.beaches = beaches
        self.badges = badges

        self.create_routes()

    def create_routes(self):
        @self.app.route("/beach/<beach>")
        def beach_badge(beach):
            info = self.beaches.totals_for(beach)
            if info is None:
                response.status = 404
            else:
                badge = self.badges.create_beach_badge(
                    beach=info["bathing"],
                    spills=int(info["total_spill_count"]),
                    company=info["company_name"],
                    year=int(info["reporting_year"])
                )

                buffer = BytesIO()
                badge.convert("RGB").save(buffer, "jpeg", optimize=True)

                response.status = 200
                response.content_type = 'image/jpg'
                return buffer.getvalue()


if __name__ == '__main__':
    port = int(os.environ.get("PORT", 80))
    data_uri = os.environ.get("DATA_URI", "http://data/data")

    badges = Badges()
    beaches = Beaches(base_uri=data_uri)

    app = bottle.Bottle()
    beach_badges = BeachBadges(app=app, beaches=beaches, badges=badges)

    app.run(server='gunicorn', host='0.0.0.0', port=port)
